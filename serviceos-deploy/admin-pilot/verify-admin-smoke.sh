#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
compose_file="${repo_root}/serviceos-deploy/compose.yaml"
backend_log="${TMPDIR:-/tmp}/serviceos-admin-pilot-backend.log"
admin_log="${TMPDIR:-/tmp}/serviceos-admin-pilot-web.log"
byd_stub_log="${TMPDIR:-/tmp}/serviceos-admin-pilot-byd-stub.log"
backend_pid=""
admin_pid=""
byd_stub_pid=""
byd_stub_port=18090

cleanup() {
  [[ -z "${admin_pid}" ]] || kill "${admin_pid}" 2>/dev/null || true
  [[ -z "${backend_pid}" ]] || kill "${backend_pid}" 2>/dev/null || true
  [[ -z "${byd_stub_pid}" ]] || kill "${byd_stub_pid}" 2>/dev/null || true
}
trap cleanup EXIT

wait_http() {
  local url="$1"
  local label="$2"
  for _ in $(seq 1 90); do
    if curl --fail --silent "${url}" >/dev/null; then
      return 0
    fi
    sleep 1
  done
  echo "${label} 未在超时内就绪" >&2
  return 1
}

query_db() {
  local sql="$1"
  docker compose -f "${compose_file}" exec -T postgres \
    psql -U serviceos_app -d serviceos -Atc "${sql}"
}

new_uuid() {
  if command -v uuidgen >/dev/null 2>&1; then
    uuidgen | tr '[:upper:]' '[:lower:]'
  else
    # CI/云环境可能没有 uuidgen；python 标准库足够生成 RFC UUID。
    python3 - <<'PY'
import uuid
print(uuid.uuid4())
PY
  fi
}

# M144：断言 Admin HTTP 人工初派留下的 ACTIVE 责任 / CONFIRMED reservation / COMPLETED saga。
assert_manual_service_assignment() {
  local task_id="$1"
  local run_key="$2"
  local sa_state
  sa_state="$(query_db "
    SELECT count(DISTINCT CASE WHEN a.responsibility_level = 'NETWORK'
                                AND a.assignee_id = 'admin-pilot-network-1'
                                AND a.status = 'ACTIVE' THEN a.service_assignment_id END) || ':' ||
           count(DISTINCT CASE WHEN a.responsibility_level = 'TECHNICIAN'
                                AND a.assignee_id = '06b612f3-a901-4b0e-bd90-86b4259cc087'
                                AND a.status = 'ACTIVE' THEN a.service_assignment_id END) || ':' ||
           count(DISTINCT CASE WHEN r.status = 'CONFIRMED' THEN r.capacity_reservation_id END) || ':' ||
           count(DISTINCT CASE WHEN s.stage = 'COMPLETED' THEN s.activation_saga_id END) || ':' ||
           count(DISTINCT CASE WHEN a.created_by = '06b612f3-a901-4b0e-bd90-86b4259cc087'
                                THEN a.service_assignment_id END)
      FROM dsp_service_assignment a
      LEFT JOIN dsp_capacity_reservation r
        ON r.tenant_id = a.tenant_id
       AND r.service_assignment_id = a.service_assignment_id
      LEFT JOIN dsp_service_assignment_activation_saga s
        ON s.tenant_id = a.tenant_id
       AND s.task_id = a.task_id
       AND s.new_service_assignment_id = a.service_assignment_id
     WHERE a.tenant_id = 'tenant-local'
       AND a.task_id = '${task_id}'
  ")"
  [[ "${sa_state}" == "1:1:2:2:2" ]] || {
    echo "Admin HTTP ServiceAssignment 不完整 (${run_key}): ${sa_state}" >&2
    exit 1
  }
}

cd "${repo_root}"
docker compose -f "${compose_file}" up -d postgres keycloak
wait_http "http://127.0.0.1:8081/realms/serviceos/.well-known/openid-configuration" "Keycloak"

# 本地协议 stub：只证明 errno=0 严格 ACK 路径，不宣称真实 sandbox。
python3 - <<'PY' >"${byd_stub_log}" 2>&1 &
from http.server import BaseHTTPRequestHandler, HTTPServer

class Handler(BaseHTTPRequestHandler):
    def do_POST(self):
        length = int(self.headers.get("Content-Length", "0"))
        _ = self.rfile.read(length)
        body = b'{"errno":0,"errmsg":"ok","data":null}'
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, fmt, *args):
        return

HTTPServer(("127.0.0.1", 18090), Handler).serve_forever()
PY
byd_stub_pid="$!"

# 出站夹具要求 Backend 带着 stub URL/凭据版本启动；若已有旧进程则重启，避免漏配环境变量。
if curl --fail --silent "http://127.0.0.1:8080/livez" >/dev/null; then
  pkill -f 'serviceos-backend-0.1.0-SNAPSHOT.jar' 2>/dev/null || true
  sleep 2
fi
./mvnw --batch-mode --no-transfer-progress -pl serviceos-backend -am -DskipTests package
# complete 只在命令事务内追加 task.completed；必须启用真实 Outbox worker，
# 由 Inbox 去重消费者继续推进 Node/Stage/Workflow/WorkOrder，不能用 SQL 模拟异步结果。
# 浏览器通过 Vite 同源代理执行私有 PUT，避免把本地 HMAC 上传 URL 暴露为跨域地址；
# Backend 仍负责签发短期 token、校验大小/摘要/MIME 并在 Finalize 后调度扫描。
# 入站 CREATE_WORK_ORDER 必须解析到试点项目 ADMIN-PILOT，不能沿用默认 BYD-OCEAN-SD-PILOT。
SERVICEOS_OUTBOX_SCHEDULING_ENABLED=true \
SERVICEOS_TASK_SCHEDULING_ENABLED=true \
SERVICEOS_FILE_TRANSFER_BASE_URL=http://127.0.0.1:5173/api/v1/file-transfers \
SERVICEOS_BYD_CPIM_OUTBOUND_BASE_URL="http://127.0.0.1:${byd_stub_port}" \
SERVICEOS_BYD_CPIM_CREDENTIAL_VERSION_ID=local-admin-pilot-byd-cred-v1 \
SERVICEOS_BYD_CPIM_TENANT_ID=tenant-local \
SERVICEOS_BYD_CPIM_PROJECT_CODE=ADMIN-PILOT \
SERVICEOS_BYD_CPIM_ADAPTER_PRINCIPAL_ID=service-byd-cpim-adapter \
  java -jar serviceos-backend/target/serviceos-backend-0.1.0-SNAPSHOT.jar >"${backend_log}" 2>&1 &
backend_pid="$!"
wait_http "http://127.0.0.1:8080/readyz" "ServiceOS Backend"

docker compose -f "${compose_file}" exec -T postgres \
  psql -U serviceos_app -d serviceos \
  < serviceos-deploy/keycloak/grant-local-project-admin.sql
docker compose -f "${compose_file}" exec -T postgres \
  psql -U serviceos_app -d serviceos \
  < serviceos-deploy/admin-pilot/seed-admin-pilot.sql

# 终态验证每轮创建全新的 WorkOrder/Workflow/Stage/Node/Task，绝不通过 SQL 回退终态事实。
completion_work_order_id="$(new_uuid)"
completion_workflow_id="$(new_uuid)"
completion_stage_id="$(new_uuid)"
completion_node_id="$(new_uuid)"
completion_task_id="$(new_uuid)"
completion_start_event_id="$(new_uuid)"
completion_stage_event_id="$(new_uuid)"
completion_node_event_id="$(new_uuid)"
completion_task_created_outbox_id="$(new_uuid)"
completion_task_created_event_id="$(new_uuid)"
completion_external_code="ADMIN-PILOT-COMPLETE-${completion_work_order_id%%-*}"
completion_correlation_id="admin-pilot-complete-${completion_task_id}"

# 整改验证使用独立 WorkOrder/Workflow/Task，避免把驳回、豁免或 Task 取消事实
# 混入正常 APPROVED 后的终态推进证明。
correction_work_order_id="$(new_uuid)"
correction_workflow_id="$(new_uuid)"
correction_stage_id="$(new_uuid)"
correction_node_id="$(new_uuid)"
correction_task_id="$(new_uuid)"
correction_start_event_id="$(new_uuid)"
correction_stage_event_id="$(new_uuid)"
correction_node_event_id="$(new_uuid)"
correction_task_created_outbox_id="$(new_uuid)"
correction_task_created_event_id="$(new_uuid)"
correction_external_code="ADMIN-PILOT-CORRECTION-${correction_work_order_id%%-*}"
correction_correlation_id="admin-pilot-correction-${correction_task_id}"

# 强制通过/重开血缘使用第三个独立 Task，避免与普通批准或整改豁免共享 ReviewCase。
reopen_work_order_id="$(new_uuid)"
reopen_workflow_id="$(new_uuid)"
reopen_stage_id="$(new_uuid)"
reopen_node_id="$(new_uuid)"
reopen_task_id="$(new_uuid)"
reopen_start_event_id="$(new_uuid)"
reopen_stage_event_id="$(new_uuid)"
reopen_node_event_id="$(new_uuid)"
reopen_task_created_outbox_id="$(new_uuid)"
reopen_task_created_event_id="$(new_uuid)"
reopen_external_code="ADMIN-PILOT-REOPEN-${reopen_work_order_id%%-*}"
reopen_correlation_id="admin-pilot-reopen-${reopen_task_id}"

# 正常补传/关闭/复审/完结使用第四个独立 Task，避免与 WAIVE 或 reopen 路径共享 Case。
resubmit_work_order_id="$(new_uuid)"
resubmit_workflow_id="$(new_uuid)"
resubmit_stage_id="$(new_uuid)"
resubmit_node_id="$(new_uuid)"
resubmit_task_id="$(new_uuid)"
resubmit_start_event_id="$(new_uuid)"
resubmit_stage_event_id="$(new_uuid)"
resubmit_node_event_id="$(new_uuid)"
resubmit_task_created_outbox_id="$(new_uuid)"
resubmit_task_created_event_id="$(new_uuid)"
resubmit_external_code="ADMIN-PILOT-RESUBMIT-${resubmit_work_order_id%%-*}"
resubmit_correlation_id="admin-pilot-resubmit-${resubmit_task_id}"

# 预约/上门写链路使用第五个独立 Task，并注入 ACTIVE ServiceAssignment 以对齐 Visit 责任。
field_ops_work_order_id="$(new_uuid)"
field_ops_workflow_id="$(new_uuid)"
field_ops_stage_id="$(new_uuid)"
field_ops_node_id="$(new_uuid)"
field_ops_task_id="$(new_uuid)"
field_ops_start_event_id="$(new_uuid)"
field_ops_stage_event_id="$(new_uuid)"
field_ops_node_event_id="$(new_uuid)"
field_ops_task_created_outbox_id="$(new_uuid)"
field_ops_task_created_event_id="$(new_uuid)"
field_ops_external_code="ADMIN-PILOT-FIELD-${field_ops_work_order_id%%-*}"
field_ops_correlation_id="admin-pilot-field-ops-${field_ops_task_id}"

# BYD 提审外发使用第六套独立 Task，并登记 CREATE_WORK_ORDER Envelope/Canonical 系谱。
outbound_work_order_id="$(new_uuid)"
outbound_workflow_id="$(new_uuid)"
outbound_stage_id="$(new_uuid)"
outbound_node_id="$(new_uuid)"
outbound_task_id="$(new_uuid)"
outbound_start_event_id="$(new_uuid)"
outbound_stage_event_id="$(new_uuid)"
outbound_node_event_id="$(new_uuid)"
outbound_task_created_outbox_id="$(new_uuid)"
outbound_task_created_event_id="$(new_uuid)"
outbound_envelope_id="$(new_uuid)"
outbound_canonical_id="$(new_uuid)"
outbound_external_code="ADMIN-PILOT-OUTBOUND-${outbound_work_order_id%%-*}"
outbound_correlation_id="admin-pilot-outbound-${outbound_task_id}"
outbound_order_code="PILOT-${outbound_work_order_id%%-*}"
outbound_business_key="BYD:INSTALL:${outbound_order_code}"
outbound_transport_dedup="$(python3 - <<PY
import hashlib
print(hashlib.sha256(b"admin-pilot-outbound-${outbound_work_order_id}").hexdigest())
PY
)"
outbound_payload_digest="$(python3 - <<PY
import hashlib
print(hashlib.sha256(b"admin-pilot-canonical-${outbound_work_order_id}").hexdigest())
PY
)"
outbound_object_ref="admin-pilot/inbound/${outbound_work_order_id}.json"
outbound_external_message_id="nonce-${outbound_work_order_id}"

seed_completion_fixture() {
  local work_order_id="$1"
  local workflow_id="$2"
  local stage_id="$3"
  local node_id="$4"
  local task_id="$5"
  local start_event_id="$6"
  local stage_event_id="$7"
  local node_event_id="$8"
  local task_created_outbox_id="$9"
  local task_created_event_id="${10}"
  local external_code="${11}"
  local correlation_id="${12}"
  docker compose -f "${compose_file}" exec -T postgres \
    psql -U serviceos_app -d serviceos \
    -v completion_work_order_id="${work_order_id}" \
    -v completion_workflow_id="${workflow_id}" \
    -v completion_stage_id="${stage_id}" \
    -v completion_node_id="${node_id}" \
    -v completion_task_id="${task_id}" \
    -v completion_start_event_id="${start_event_id}" \
    -v completion_stage_event_id="${stage_event_id}" \
    -v completion_node_event_id="${node_event_id}" \
    -v completion_task_created_outbox_id="${task_created_outbox_id}" \
    -v completion_task_created_event_id="${task_created_event_id}" \
    -v completion_external_code="${external_code}" \
    -v completion_correlation_id="${correlation_id}" \
    < serviceos-deploy/admin-pilot/seed-admin-completion.sql
}

seed_completion_fixture \
  "${completion_work_order_id}" "${completion_workflow_id}" "${completion_stage_id}" \
  "${completion_node_id}" "${completion_task_id}" "${completion_start_event_id}" \
  "${completion_stage_event_id}" "${completion_node_event_id}" \
  "${completion_task_created_outbox_id}" "${completion_task_created_event_id}" \
  "${completion_external_code}" "${completion_correlation_id}"

seed_completion_fixture \
  "${correction_work_order_id}" "${correction_workflow_id}" "${correction_stage_id}" \
  "${correction_node_id}" "${correction_task_id}" "${correction_start_event_id}" \
  "${correction_stage_event_id}" "${correction_node_event_id}" \
  "${correction_task_created_outbox_id}" "${correction_task_created_event_id}" \
  "${correction_external_code}" "${correction_correlation_id}"

seed_completion_fixture \
  "${reopen_work_order_id}" "${reopen_workflow_id}" "${reopen_stage_id}" \
  "${reopen_node_id}" "${reopen_task_id}" "${reopen_start_event_id}" \
  "${reopen_stage_event_id}" "${reopen_node_event_id}" \
  "${reopen_task_created_outbox_id}" "${reopen_task_created_event_id}" \
  "${reopen_external_code}" "${reopen_correlation_id}"

seed_completion_fixture \
  "${resubmit_work_order_id}" "${resubmit_workflow_id}" "${resubmit_stage_id}" \
  "${resubmit_node_id}" "${resubmit_task_id}" "${resubmit_start_event_id}" \
  "${resubmit_stage_event_id}" "${resubmit_node_event_id}" \
  "${resubmit_task_created_outbox_id}" "${resubmit_task_created_event_id}" \
  "${resubmit_external_code}" "${resubmit_correlation_id}"

seed_completion_fixture \
  "${field_ops_work_order_id}" "${field_ops_workflow_id}" "${field_ops_stage_id}" \
  "${field_ops_node_id}" "${field_ops_task_id}" "${field_ops_start_event_id}" \
  "${field_ops_stage_event_id}" "${field_ops_node_event_id}" \
  "${field_ops_task_created_outbox_id}" "${field_ops_task_created_event_id}" \
  "${field_ops_external_code}" "${field_ops_correlation_id}"

seed_completion_fixture \
  "${outbound_work_order_id}" "${outbound_workflow_id}" "${outbound_stage_id}" \
  "${outbound_node_id}" "${outbound_task_id}" "${outbound_start_event_id}" \
  "${outbound_stage_event_id}" "${outbound_node_event_id}" \
  "${outbound_task_created_outbox_id}" "${outbound_task_created_event_id}" \
  "${outbound_external_code}" "${outbound_correlation_id}"

docker compose -f "${compose_file}" exec -T postgres \
  psql -U serviceos_app -d serviceos \
  -v outbound_work_order_id="${outbound_work_order_id}" \
  -v outbound_envelope_id="${outbound_envelope_id}" \
  -v outbound_canonical_id="${outbound_canonical_id}" \
  -v outbound_business_key="${outbound_business_key}" \
  -v outbound_transport_dedup="${outbound_transport_dedup}" \
  -v outbound_payload_digest="${outbound_payload_digest}" \
  -v outbound_object_ref="${outbound_object_ref}" \
  -v outbound_external_message_id="${outbound_external_message_id}" \
  -v outbound_correlation_id="${outbound_correlation_id}" \
  < serviceos-deploy/admin-pilot/seed-admin-byd-lineage.sql

if ! curl --fail --silent "http://127.0.0.1:5173" >/dev/null; then
  (
    cd serviceos-admin-web
    VITE_DEV_OIDC_ENABLED=true npm run dev -- --host 127.0.0.1
  ) >"${admin_log}" 2>&1 &
  admin_pid="$!"
fi
wait_http "http://127.0.0.1:5173" "ServiceOS Admin Web"

# M140/M144：真实 CPIM 入站创建工单 → Outbox 激活 Workflow/Task；Playwright 经 Admin HTTP
# 人工初派后证明领取/预约上门/整改外发完结（ADMIN-PILOT-09）。
inbound_order_uuid="$(new_uuid)"
inbound_order_code="ADMIN-PILOT-IN-${inbound_order_uuid%%-*}"

python3 - <<PY
import hashlib
import json
import urllib.request
import uuid
from datetime import datetime
from zoneinfo import ZoneInfo

app_key = "local-byd-app-key"
app_secret = "local-byd-app-secret-change-me"
order_code = "${inbound_order_code}"
vin_suffix = uuid.uuid4().hex[:6].upper()
payload = {
    "orderCode": order_code,
    "contactName": "Admin 试点入站用户",
    "contactMobile": "13900000001",
    "contactAddress": "山东省济南市历下区入站试点路1号",
    "provinceCode": "370000",
    "provinceName": "山东省",
    "cityCode": "370100",
    "cityName": "济南市",
    "areaCode": "370102",
    "areaName": "历下区",
    "wallboxName": "比亚迪7kW交流充电桩",
    "wallboxPower": "7kW",
    "bringWallbox": "1",
    "dispatchTime": "2026-07-16T10:00:00",
    "carOwnerType": "1",
    "type": "1",
    "carBrand": "40",
    "carSeries": "海豹",
    "carModel": "海豹06 DM-i",
    "vin": f"LGXCE6CD0RA{vin_suffix}",
    "dealerName": "济南海洋网经销商",
    "rightCode": f"RIGHT-IN-{order_code[-8:]}",
    "orderAmount": 0,
    "source": "1",
    "channel": "CPIM",
}
nonce = str(uuid.uuid4())
current_date = datetime.now(ZoneInfo("Asia/Shanghai")).date().isoformat()
params = "&".join(f"{key}={value}" for key, value in sorted(
    (key, str(value)) for key, value in payload.items()
))
signature = hashlib.sha256(
    f"{app_secret}&{nonce}&{current_date}&{params}".encode("utf-8")
).hexdigest()
body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
request = urllib.request.Request(
    "http://127.0.0.1:8080/api/v1/integrations/byd/cpim/v7.3.1/install-orders",
    data=body,
    method="POST",
    headers={
        "Content-Type": "application/json",
        "APP_KEY": app_key,
        "Nonce": nonce,
        "Cur_Time": current_date,
        "Sign": signature,
        "X-Correlation-Id": f"admin-pilot-inbound-{order_code}",
    },
)
with urllib.request.urlopen(request, timeout=30) as response:
    result = json.loads(response.read().decode("utf-8"))
if not result.get("success") or result.get("code") != "ACCEPTED":
    raise SystemExit(f"入站 CREATE_WORK_ORDER 失败: {result}")
print(f"inbound ACCEPTED orderCode={order_code}")
PY

inbound_work_order_id=""
inbound_task_id=""
for _ in $(seq 1 60); do
  inbound_ids="$(query_db "
    SELECT work_order.id::text || '|' || task.task_id::text
      FROM wo_work_order work_order
      JOIN tsk_task task
        ON task.tenant_id = work_order.tenant_id
       AND task.work_order_id = work_order.id
       AND task.task_kind = 'HUMAN'
       AND task.status = 'READY'
     WHERE work_order.tenant_id = 'tenant-local'
       AND work_order.external_order_code = '${inbound_order_code}'
       AND work_order.status = 'ACTIVE'
     ORDER BY task.created_at
     LIMIT 1
  ")"
  if [[ "${inbound_ids}" == *"|"* ]]; then
    inbound_work_order_id="${inbound_ids%%|*}"
    inbound_task_id="${inbound_ids#*|}"
    break
  fi
  sleep 1
done
[[ -n "${inbound_work_order_id}" && -n "${inbound_task_id}" ]] || {
  echo "入站工单未在超时内激活并创建 HUMAN Task: ${inbound_order_code}" >&2
  exit 1
}

cd serviceos-admin-web
export ADMIN_PILOT_COMPLETION_WORK_ORDER_CODE="${completion_external_code}"
export ADMIN_PILOT_COMPLETION_TASK_ID="${completion_task_id}"
export ADMIN_PILOT_CORRECTION_WORK_ORDER_CODE="${correction_external_code}"
export ADMIN_PILOT_CORRECTION_TASK_ID="${correction_task_id}"
export ADMIN_PILOT_REOPEN_WORK_ORDER_CODE="${reopen_external_code}"
export ADMIN_PILOT_REOPEN_TASK_ID="${reopen_task_id}"
export ADMIN_PILOT_RESUBMIT_WORK_ORDER_CODE="${resubmit_external_code}"
export ADMIN_PILOT_RESUBMIT_TASK_ID="${resubmit_task_id}"
export ADMIN_PILOT_FIELD_OPS_WORK_ORDER_CODE="${field_ops_external_code}"
export ADMIN_PILOT_FIELD_OPS_TASK_ID="${field_ops_task_id}"
export ADMIN_PILOT_OUTBOUND_WORK_ORDER_CODE="${outbound_external_code}"
export ADMIN_PILOT_OUTBOUND_TASK_ID="${outbound_task_id}"
export ADMIN_PILOT_INBOUND_ORDER_CODE="${inbound_order_code}"
export ADMIN_PILOT_INBOUND_TASK_ID="${inbound_task_id}"
npm run test:e2e

task_state="$(query_db "
  SELECT status || ':' || version || ':' || COALESCE(claimed_by, '')
    FROM tsk_task
   WHERE task_id = '70000000-0000-4000-8000-000000000001'
")"
[[ "${task_state}" =~ ^READY:[0-9]+:$ ]] || {
  echo "Admin 试点 Task 最终状态非法: ${task_state}" >&2
  exit 1
}

active_candidate_count="$(query_db "
  SELECT count(*)
    FROM tsk_task_assignment
   WHERE task_id = '70000000-0000-4000-8000-000000000001'
     AND assignment_kind = 'CANDIDATE'
     AND principal_id = '06b612f3-a901-4b0e-bd90-86b4259cc087'
     AND status = 'ACTIVE'
")"
[[ "${active_candidate_count}" == "1" ]] || {
  echo "Admin 试点 ACTIVE 候选事实数量非法: ${active_candidate_count}" >&2
  exit 1
}

latest_assignment_batch="$(query_db "
  SELECT source_type || ':' || source_id || ':' || candidate_count
    FROM tsk_task_assignment_batch
   WHERE task_id = '70000000-0000-4000-8000-000000000001'
   ORDER BY assigned_at DESC
   LIMIT 1
")"
[[ "${latest_assignment_batch}" == "MANUAL:admin-pilot-e2e:1" ]] || {
  echo "Admin 试点最新候选批次不是页面 MANUAL 分配结果: ${latest_assignment_batch}" >&2
  exit 1
}

active_responsible_count="$(query_db "
  SELECT count(*)
    FROM tsk_task_assignment
   WHERE task_id = '70000000-0000-4000-8000-000000000001'
     AND assignment_kind = 'RESPONSIBLE'
     AND status = 'ACTIVE'
")"
[[ "${active_responsible_count}" == "0" ]] || {
  echo "Admin 试点 release 后仍存在 ACTIVE 责任事实: ${active_responsible_count}" >&2
  exit 1
}

successful_audit_action_count="$(query_db "
  SELECT count(DISTINCT action_name)
    FROM aud_audit_record
   WHERE target_id = '70000000-0000-4000-8000-000000000001'
     AND action_name IN (
       'TASK_ASSIGN_CANDIDATES', 'TASK_HUMAN_CLAIM', 'TASK_HUMAN_RELEASE'
     )
     AND result_code = 'SUCCEEDED'
")"
[[ "${successful_audit_action_count}" == "3" ]] || {
  echo "Admin 试点候选分配/领取/释放成功审计不完整: ${successful_audit_action_count}" >&2
  exit 1
}

completion_state=""
for _ in $(seq 1 60); do
  completion_state="$(query_db "
    SELECT task.status || ':' || node.status || ':' || stage.status || ':' ||
           workflow.status || ':' || work_order.status
      FROM tsk_task task
      JOIN wfl_node_instance node
        ON node.tenant_id = task.tenant_id
       AND node.workflow_node_instance_id = task.workflow_node_instance_id
      JOIN wfl_stage_instance stage
        ON stage.tenant_id = task.tenant_id
       AND stage.stage_instance_id = task.stage_instance_id
      JOIN wfl_workflow_instance workflow
        ON workflow.tenant_id = task.tenant_id
       AND workflow.workflow_instance_id = task.workflow_instance_id
      JOIN wo_work_order work_order
        ON work_order.tenant_id = task.tenant_id
       AND work_order.id = task.work_order_id
     WHERE task.task_id = '${completion_task_id}'
  ")"
  [[ "${completion_state}" == "COMPLETED:COMPLETED:COMPLETED:COMPLETED:FULFILLED" ]] && break
  sleep 1
done
[[ "${completion_state}" == "COMPLETED:COMPLETED:COMPLETED:COMPLETED:FULFILLED" ]] || {
  echo "Admin 试点终态推进未闭环: ${completion_state}" >&2
  exit 1
}

expired_assignment_count="$(query_db "
  SELECT count(*)
    FROM tsk_task_assignment
   WHERE task_id = '${completion_task_id}'
     AND assignment_kind IN ('CANDIDATE', 'RESPONSIBLE')
     AND status = 'EXPIRED'
")"
[[ "${expired_assignment_count}" == "2" ]] || {
  echo "Admin 试点 complete 后候选/责任未全部过期: ${expired_assignment_count}" >&2
  exit 1
}

completion_audit_count="$(query_db "
  SELECT count(DISTINCT action_name)
    FROM aud_audit_record
   WHERE target_id = '${completion_task_id}'
     AND action_name IN (
       'TASK_ASSIGN_CANDIDATES', 'TASK_HUMAN_CLAIM',
       'TASK_HUMAN_START', 'TASK_HUMAN_COMPLETE'
     )
     AND result_code = 'SUCCEEDED'
")"
[[ "${completion_audit_count}" == "4" ]] || {
  echo "Admin 试点终态命令成功审计不完整: ${completion_audit_count}" >&2
  exit 1
}

form_submission_state="$(query_db "
  SELECT submission.validation_status || ':' ||
         (task.result_ref = 'form-submission://' || submission.form_submission_id::text) || ':' ||
         (task.result_digest = submission.content_digest)
    FROM frm_form_submission submission
    JOIN tsk_task task
      ON task.tenant_id = submission.tenant_id
     AND task.task_id = submission.task_id
   WHERE submission.task_id = '${completion_task_id}'
   ORDER BY submission.submission_version DESC
   LIMIT 1
")"
[[ "${form_submission_state}" == "VALIDATED:true:true" ]] || {
  echo "Admin 试点表单提交未形成精确 complete 引用: ${form_submission_state}" >&2
  exit 1
}

form_submission_audit_count="$(query_db "
  SELECT count(*)
    FROM aud_audit_record audit
    JOIN frm_form_submission submission
      ON submission.tenant_id = audit.tenant_id
     AND submission.form_submission_id::text = audit.target_id
   WHERE submission.task_id = '${completion_task_id}'
     AND audit.action_name = 'FORM_SUBMITTED'
     AND audit.capability_code = 'form.submit'
     AND audit.result_code = 'VALIDATED'
")"
[[ "${form_submission_audit_count}" == "1" ]] || {
  echo "Admin 试点表单提交成功审计不完整: ${form_submission_audit_count}" >&2
  exit 1
}

evidence_completion_state="$(query_db "
  SELECT revision.status || ':' || stored_file.lifecycle_status || ':' ||
         snapshot.purpose || ':' || snapshot.member_count || ':' ||
         (
           task.input_version_refs @> jsonb_build_array(jsonb_build_object(
             'kind', 'FORM_SUBMISSION',
             'ref', task.result_ref,
             'digest', task.result_digest
           ))
         ) || ':' ||
         (
           task.input_version_refs @> jsonb_build_array(jsonb_build_object(
             'kind', 'EVIDENCE_SET_SNAPSHOT',
             'ref', 'evidence-set-snapshot://' || snapshot.evidence_set_snapshot_id::text,
             'digest', snapshot.content_digest
           ))
         )
    FROM tsk_task task
    JOIN evd_evidence_set_snapshot snapshot
      ON snapshot.tenant_id = task.tenant_id
     AND snapshot.task_id = task.task_id
    JOIN evd_evidence_set_member member
      ON member.tenant_id = snapshot.tenant_id
     AND member.evidence_set_snapshot_id = snapshot.evidence_set_snapshot_id
    JOIN evd_evidence_revision revision
      ON revision.tenant_id = member.tenant_id
     AND revision.evidence_revision_id = member.evidence_revision_id
    JOIN fil_stored_file stored_file
      ON stored_file.tenant_id = revision.tenant_id
     AND stored_file.file_id = revision.file_object_id
   WHERE task.task_id = '${completion_task_id}'
   ORDER BY snapshot.created_at DESC
   LIMIT 1
")"
[[ "${evidence_completion_state}" == "VALIDATED:AVAILABLE:TASK_SUBMISSION:1:true:true" ]] || {
  echo "Admin 试点资料未形成可用文件、VALIDATED Revision 与精确双引用: ${evidence_completion_state}" >&2
  exit 1
}

evidence_audit_count="$(query_db "
  SELECT count(DISTINCT action_name)
    FROM aud_audit_record
   WHERE action_name IN (
       'EVIDENCE_UPLOAD_BEGUN',
       'FILE_UPLOAD_SESSION_CREATED',
       'FILE_UPLOAD_FINALIZED',
       'EVIDENCE_REVISION_CREATED',
       'EVIDENCE_MACHINE_VALIDATION_COMPLETED',
       'EVIDENCE_SET_SNAPSHOT_CREATED'
     )
     AND (
       target_id = '${completion_task_id}'
       OR target_id IN (
         SELECT slot_id::text
           FROM evd_evidence_slot
          WHERE task_id = '${completion_task_id}'
         UNION
         SELECT evidence_revision_id::text
           FROM evd_evidence_revision
          WHERE task_id = '${completion_task_id}'
         UNION
         SELECT evidence_set_snapshot_id::text
           FROM evd_evidence_set_snapshot
          WHERE task_id = '${completion_task_id}'
       )
     )
")"
[[ "${evidence_audit_count}" -ge 4 ]] || {
  echo "Admin 试点资料上传/校验/快照审计不完整: ${evidence_audit_count}" >&2
  exit 1
}

review_completion_state=""
for _ in $(seq 1 30); do
  review_completion_state="$(query_db "
    SELECT review.status || ':' || count(DISTINCT decision.review_decision_id) || ':' ||
           count(DISTINCT audit.action_name) || ':' ||
           count(DISTINCT inbox.event_id)
      FROM evd_review_case review
      LEFT JOIN evd_review_decision decision
        ON decision.tenant_id = review.tenant_id
       AND decision.review_case_id = review.review_case_id
      LEFT JOIN aud_audit_record audit
        ON audit.tenant_id = review.tenant_id
       AND audit.target_id = review.review_case_id::text
       AND audit.action_name IN ('REVIEW_CASE_CREATED', 'REVIEW_CASE_DECIDED')
      LEFT JOIN rel_outbox_event outbox
        ON outbox.tenant_id = review.tenant_id
       AND outbox.aggregate_type = 'ReviewCase'
       AND outbox.aggregate_id = review.review_case_id::text
       AND outbox.event_type IN ('evidence.review-case-created', 'evidence.review-decided')
      LEFT JOIN rel_inbox_record inbox
        ON inbox.tenant_id = outbox.tenant_id
       AND inbox.event_id = outbox.event_id
       AND inbox.status = 'SUCCEEDED'
     WHERE review.task_id = '${completion_task_id}'
       AND review.origin = 'INTERNAL'
     GROUP BY review.review_case_id, review.status
  ")"
  [[ "${review_completion_state}" == "APPROVED:1:2:2" ]] && break
  sleep 1
done
[[ "${review_completion_state}" == "APPROVED:1:2:2" ]] || {
  echo "Admin 试点审核案例、唯一裁决、审计或事件消费不完整: ${review_completion_state}" >&2
  exit 1
}

correction_waive_state=""
for _ in $(seq 1 30); do
  correction_waive_state="$(query_db "
    SELECT review.status || ':' ||
           count(DISTINCT decision.review_decision_id) || ':' ||
           correction.status || ':' ||
           correction_task.status || ':' ||
           count(DISTINCT audit.action_name) || ':' ||
           count(DISTINCT inbox.event_id)
      FROM evd_review_case review
      JOIN evd_review_decision decision
        ON decision.tenant_id = review.tenant_id
       AND decision.review_case_id = review.review_case_id
      JOIN evd_correction_case correction
        ON correction.tenant_id = decision.tenant_id
       AND correction.source_review_decision_id = decision.review_decision_id
      JOIN tsk_task correction_task
        ON correction_task.tenant_id = correction.tenant_id
       AND correction_task.task_id = correction.correction_task_id
      LEFT JOIN aud_audit_record audit
        ON audit.tenant_id = correction.tenant_id
       AND (
         (
           audit.target_id = review.review_case_id::text
           AND audit.action_name IN ('REVIEW_CASE_CREATED', 'REVIEW_CASE_DECIDED')
         )
         OR (
           audit.target_id = correction.correction_case_id::text
           AND audit.action_name = 'CORRECTION_CASE_WAIVED'
         )
       )
      LEFT JOIN rel_outbox_event outbox
        ON outbox.tenant_id = correction.tenant_id
       AND (
         (
           outbox.aggregate_type = 'ReviewCase'
           AND outbox.aggregate_id = review.review_case_id::text
           AND outbox.event_type IN ('evidence.review-case-created', 'evidence.review-decided')
         )
         OR (
           outbox.aggregate_type = 'CorrectionCase'
           AND outbox.aggregate_id = correction.correction_case_id::text
           AND outbox.event_type IN (
             'evidence.correction-case-created',
             'evidence.correction-waived'
           )
         )
       )
      LEFT JOIN rel_inbox_record inbox
        ON inbox.tenant_id = outbox.tenant_id
       AND inbox.event_id = outbox.event_id
       AND inbox.status = 'SUCCEEDED'
     WHERE review.task_id = '${correction_task_id}'
       AND review.origin = 'INTERNAL'
     GROUP BY review.review_case_id, review.status, correction.correction_case_id,
              correction.status, correction_task.status
  ")"
  [[ "${correction_waive_state}" == "REJECTED:1:WAIVED:CANCELLED:3:4" ]] && break
  sleep 1
done
[[ "${correction_waive_state}" == "REJECTED:1:WAIVED:CANCELLED:3:4" ]] || {
  echo "Admin 试点驳回、整改豁免、Task 取消、审计或事件消费不完整: ${correction_waive_state}" >&2
  exit 1
}

review_reopen_state=""
for _ in $(seq 1 30); do
  review_reopen_state="$(query_db "
    SELECT source.status || ':' ||
           decision.decision || ':' ||
           successor.status || ':' ||
           (successor.reopened_from_review_case_id = source.review_case_id) || ':' ||
           successor.reopen_trigger_ref || ':' ||
           count(DISTINCT audit.action_name) || ':' ||
           count(DISTINCT inbox.event_id) || ':' ||
           count(DISTINCT correction.correction_case_id)
      FROM evd_review_case source
      JOIN evd_review_decision decision
        ON decision.tenant_id = source.tenant_id
       AND decision.review_case_id = source.review_case_id
      JOIN evd_review_case successor
        ON successor.tenant_id = source.tenant_id
       AND successor.reopened_from_review_case_id = source.review_case_id
      LEFT JOIN evd_correction_case correction
        ON correction.tenant_id = source.tenant_id
       AND correction.source_review_decision_id = decision.review_decision_id
      LEFT JOIN aud_audit_record audit
        ON audit.tenant_id = source.tenant_id
       AND (
         (
           audit.target_id = source.review_case_id::text
           AND audit.action_name IN ('REVIEW_CASE_CREATED', 'REVIEW_CASE_FORCE_APPROVED')
         )
         OR (
           audit.target_id = successor.review_case_id::text
           AND audit.action_name = 'REVIEW_CASE_REOPENED'
         )
       )
      LEFT JOIN rel_outbox_event outbox
        ON outbox.tenant_id = source.tenant_id
       AND (
         (
           outbox.aggregate_id = source.review_case_id::text
           AND outbox.event_type IN ('evidence.review-case-created', 'evidence.review-decided')
         )
         OR (
           outbox.aggregate_id = successor.review_case_id::text
           AND outbox.event_type = 'evidence.review-case-reopened'
         )
       )
      LEFT JOIN rel_inbox_record inbox
        ON inbox.tenant_id = outbox.tenant_id
       AND inbox.event_id = outbox.event_id
       AND inbox.status = 'SUCCEEDED'
     WHERE source.task_id = '${reopen_task_id}'
       AND source.reopened_from_review_case_id IS NULL
     GROUP BY source.review_case_id, source.status, decision.decision,
              successor.review_case_id, successor.status,
              successor.reopened_from_review_case_id, successor.reopen_trigger_ref
  ")"
  [[ "${review_reopen_state}" == \
    "REOPENED:FORCE_APPROVED:OPEN:true:OEM_REJECTION:ADMIN-PILOT-001:3:3:0" ]] && break
  sleep 1
done
[[ "${review_reopen_state}" == \
  "REOPENED:FORCE_APPROVED:OPEN:true:OEM_REJECTION:ADMIN-PILOT-001:3:3:0" ]] || {
  echo "Admin 试点强制通过、重开血缘、审计或事件消费不完整: ${review_reopen_state}" >&2
  exit 1
}

correction_resubmit_state=""
for _ in $(seq 1 60); do
  correction_resubmit_state="$(query_db "
    SELECT rejected.status || ':' ||
           correction.status || ':' ||
           count(DISTINCT resubmission.correction_resubmission_id) || ':' ||
           approved.status || ':' ||
           task.status || ':' ||
           work_order.status || ':' ||
           count(DISTINCT audit.target_id || ':' || audit.action_name) || ':' ||
           count(DISTINCT inbox.event_id)
      FROM evd_review_case rejected
      JOIN evd_review_decision rejected_decision
        ON rejected_decision.tenant_id = rejected.tenant_id
       AND rejected_decision.review_case_id = rejected.review_case_id
       AND rejected_decision.decision = 'REJECTED'
      JOIN evd_correction_case correction
        ON correction.tenant_id = rejected_decision.tenant_id
       AND correction.source_review_decision_id = rejected_decision.review_decision_id
      JOIN evd_correction_resubmission resubmission
        ON resubmission.tenant_id = correction.tenant_id
       AND resubmission.correction_case_id = correction.correction_case_id
      JOIN evd_review_case approved
        ON approved.tenant_id = rejected.tenant_id
       AND approved.task_id = rejected.task_id
       AND approved.evidence_set_snapshot_id = correction.latest_resubmission_snapshot_id
       AND approved.origin = 'INTERNAL'
      JOIN tsk_task task
        ON task.tenant_id = rejected.tenant_id
       AND task.task_id = rejected.task_id
      JOIN wo_work_order work_order
        ON work_order.tenant_id = task.tenant_id
       AND work_order.id = task.work_order_id
      LEFT JOIN aud_audit_record audit
        ON audit.tenant_id = correction.tenant_id
       AND (
         (
           audit.target_id = rejected.review_case_id::text
           AND audit.action_name IN ('REVIEW_CASE_CREATED', 'REVIEW_CASE_DECIDED')
         )
         OR (
           audit.target_id = correction.correction_case_id::text
           AND audit.action_name IN (
             'CORRECTION_CASE_RESUBMITTED', 'CORRECTION_CASE_CLOSED'
           )
         )
         OR (
           audit.target_id = approved.review_case_id::text
           AND audit.action_name IN ('REVIEW_CASE_CREATED', 'REVIEW_CASE_DECIDED')
         )
         OR (
           audit.target_id = task.task_id::text
           AND audit.action_name = 'TASK_HUMAN_COMPLETE'
         )
       )
      LEFT JOIN rel_outbox_event outbox
        ON outbox.tenant_id = correction.tenant_id
       AND (
         (
           outbox.aggregate_type = 'ReviewCase'
           AND outbox.aggregate_id IN (
             rejected.review_case_id::text, approved.review_case_id::text
           )
           AND outbox.event_type IN (
             'evidence.review-case-created', 'evidence.review-decided'
           )
         )
         OR (
           outbox.aggregate_type = 'CorrectionCase'
           AND outbox.aggregate_id = correction.correction_case_id::text
           AND outbox.event_type IN (
             'evidence.correction-case-created',
             'evidence.correction-resubmitted',
             'evidence.correction-closed'
           )
         )
         OR (
           outbox.aggregate_type = 'Task'
           AND outbox.aggregate_id = task.task_id::text
           AND outbox.event_type = 'task.completed'
         )
       )
      LEFT JOIN rel_inbox_record inbox
        ON inbox.tenant_id = outbox.tenant_id
       AND inbox.event_id = outbox.event_id
       AND inbox.status = 'SUCCEEDED'
     WHERE rejected.task_id = '${resubmit_task_id}'
       AND rejected.origin = 'INTERNAL'
       AND rejected.status = 'REJECTED'
     GROUP BY rejected.review_case_id, rejected.status, correction.correction_case_id,
              correction.status, approved.review_case_id, approved.status,
              task.task_id, task.status, work_order.status
  ")"
  # 7 = 驳回/复审各 CREATED+DECIDED + RESUBMITTED + CLOSED + TASK_HUMAN_COMPLETE
  # 8 = 两轮审核事件 + correction created/resubmitted/closed + task.completed
  [[ "${correction_resubmit_state}" == "REJECTED:CLOSED:1:APPROVED:COMPLETED:FULFILLED:7:8" ]] && break
  sleep 1
done
[[ "${correction_resubmit_state}" == "REJECTED:CLOSED:1:APPROVED:COMPLETED:FULFILLED:7:8" ]] || {
  echo "Admin 试点正常补传/关闭/复审/完结不完整: ${correction_resubmit_state}" >&2
  exit 1
}

progression_inbox_count="$(query_db "
  SELECT count(*)
    FROM rel_inbox_record
   WHERE consumer_name = 'workflow.task-completed.v1'
     AND status = 'SUCCEEDED'
     AND event_id = (
       SELECT event_id
         FROM rel_outbox_event
        WHERE aggregate_id = '${completion_task_id}'
          AND event_type = 'task.completed'
     )
")"
[[ "${progression_inbox_count}" == "1" ]] || {
  echo "Admin 试点 task.completed 未被 Workflow Inbox 成功消费: ${progression_inbox_count}" >&2
  exit 1
}

resubmit_progression_inbox_count="$(query_db "
  SELECT count(*)
    FROM rel_inbox_record
   WHERE consumer_name = 'workflow.task-completed.v1'
     AND status = 'SUCCEEDED'
     AND event_id = (
       SELECT event_id
         FROM rel_outbox_event
        WHERE aggregate_id = '${resubmit_task_id}'
          AND event_type = 'task.completed'
     )
")"
[[ "${resubmit_progression_inbox_count}" == "1" ]] || {
  echo "Admin 试点补传完结 task.completed 未被 Workflow Inbox 成功消费: ${resubmit_progression_inbox_count}" >&2
  exit 1
}

field_ops_state=""
for _ in $(seq 1 45); do
  field_ops_state="$(query_db "
    SELECT appointment.status || ':' ||
           visit.status || ':' ||
           count(DISTINCT audit.action_name) || ':' ||
           count(DISTINCT inbox.event_id)
      FROM apt_appointment appointment
      JOIN fld_visit visit
        ON visit.tenant_id = appointment.tenant_id
       AND visit.appointment_id = appointment.appointment_id
      LEFT JOIN aud_audit_record audit
        ON audit.tenant_id = appointment.tenant_id
       AND (
         (
           audit.target_id = appointment.appointment_id::text
           AND audit.action_name IN ('APPOINTMENT_PROPOSE', 'APPOINTMENT_CONFIRM')
         )
         OR (
           audit.target_id = visit.visit_id::text
           AND audit.action_name IN ('VISIT_CHECK_IN', 'VISIT_CHECK_OUT')
         )
       )
      LEFT JOIN rel_outbox_event outbox
        ON outbox.tenant_id = appointment.tenant_id
       AND (
         (
           outbox.aggregate_type = 'Appointment'
           AND outbox.aggregate_id = appointment.appointment_id::text
           AND outbox.event_type IN ('appointment.proposed', 'appointment.confirmed')
         )
         OR (
           outbox.aggregate_type = 'Visit'
           AND outbox.aggregate_id = visit.visit_id::text
           AND outbox.event_type IN ('visit.checked-in', 'visit.checked-out')
         )
       )
      LEFT JOIN rel_inbox_record inbox
        ON inbox.tenant_id = outbox.tenant_id
       AND inbox.event_id = outbox.event_id
       AND inbox.status = 'SUCCEEDED'
     WHERE appointment.task_id = '${field_ops_task_id}'
     GROUP BY appointment.appointment_id, appointment.status,
              visit.visit_id, visit.status
  ")"
  # 签退会把 Appointment 与 Visit 一并推进到 COMPLETED（同事务）。
  [[ "${field_ops_state}" == "COMPLETED:COMPLETED:4:4" ]] && break
  sleep 1
done
[[ "${field_ops_state}" == "COMPLETED:COMPLETED:4:4" ]] || {
  echo "Admin 试点预约/上门写链路不完整: ${field_ops_state}" >&2
  exit 1
}

outbound_delivery_state=""
for _ in $(seq 1 60); do
  outbound_delivery_state="$(query_db "
    SELECT delivery.status || ':' ||
           coalesce(max(attempt.status), 'NONE') || ':' ||
           count(DISTINCT ack.acknowledgement_id) || ':' ||
           (delivery.client_review_case_id IS NOT NULL)
      FROM int_outbound_delivery delivery
      LEFT JOIN int_delivery_attempt attempt
        ON attempt.tenant_id = delivery.tenant_id
       AND attempt.delivery_id = delivery.delivery_id
      LEFT JOIN int_external_acknowledgement ack
        ON ack.tenant_id = delivery.tenant_id
       AND ack.delivery_id = delivery.delivery_id
     WHERE delivery.source_review_case_id IN (
             SELECT review_case_id FROM evd_review_case
              WHERE task_id = '${outbound_task_id}' AND origin = 'INTERNAL'
           )
     GROUP BY delivery.delivery_id, delivery.status, delivery.client_review_case_id
  ")"
  [[ "${outbound_delivery_state}" == "ACKNOWLEDGED:DELIVERED:1:true" \
    || "${outbound_delivery_state}" == "ACKNOWLEDGED:DELIVERED:1:t" ]] && break
  sleep 1
done
[[ "${outbound_delivery_state}" == "ACKNOWLEDGED:DELIVERED:1:true" \
  || "${outbound_delivery_state}" == "ACKNOWLEDGED:DELIVERED:1:t" ]] || {
  echo "Admin 试点 BYD 提审外发 ACK 不完整: ${outbound_delivery_state}" >&2
  exit 1
}

callback_state=""
for _ in $(seq 1 45); do
  callback_state="$(query_db "
    SELECT client_case.status || ':' ||
           count(DISTINCT receipt.receipt_id) || ':' ||
           count(DISTINCT decision.review_decision_id) || ':' ||
           count(DISTINCT envelope.inbound_envelope_id)
      FROM int_outbound_delivery delivery
      JOIN evd_review_case client_case
        ON client_case.review_case_id = delivery.client_review_case_id
      LEFT JOIN evd_external_review_receipt receipt
        ON receipt.tenant_id = client_case.tenant_id
       AND receipt.review_case_id = client_case.review_case_id
      LEFT JOIN evd_review_decision decision
        ON decision.tenant_id = client_case.tenant_id
       AND decision.review_case_id = client_case.review_case_id
       AND decision.decision_source = 'EXTERNAL'
       AND decision.decision = 'APPROVED'
      LEFT JOIN int_canonical_message callback_canonical
        ON callback_canonical.tenant_id = delivery.tenant_id
       AND callback_canonical.message_type = 'RECORD_CLIENT_REVIEW_RESULT'
       AND callback_canonical.business_key LIKE
             'BYD:REVIEW:' || delivery.external_order_code || ':%'
      LEFT JOIN int_inbound_envelope envelope
        ON envelope.tenant_id = callback_canonical.tenant_id
       AND envelope.inbound_envelope_id = callback_canonical.source_envelope_id
       AND envelope.message_type = 'RECORD_CLIENT_REVIEW_RESULT'
       AND envelope.processing_status = 'COMPLETED'
     WHERE delivery.source_review_case_id IN (
             SELECT review_case_id FROM evd_review_case
              WHERE task_id = '${outbound_task_id}' AND origin = 'INTERNAL'
           )
     GROUP BY client_case.review_case_id, client_case.status
  ")"
  [[ "${callback_state}" == "APPROVED:1:1:1" ]] && break
  sleep 1
done
[[ "${callback_state}" == "APPROVED:1:1:1" ]] || {
  echo "Admin 试点 BYD 厂端回调联调不完整: ${callback_state}" >&2
  exit 1
}

# M142：同单入站贯通终态——FULFILLED + 系谱 + 双 Snapshot + REJECTED/CLOSED 整改 + 复审外发/回调。
inbound_state=""
for _ in $(seq 1 90); do
  inbound_state="$(query_db "
    SELECT work_order.status || ':' ||
           count(DISTINCT envelope.inbound_envelope_id) || ':' ||
           bool_or(
             canonical.business_key = 'BYD:INSTALL:' || work_order.external_order_code
           ) || ':' ||
           max(workflow.status) || ':' ||
           max(task.status) || ':' ||
           max(task.form_ref) || ':' ||
           count(DISTINCT appointment.appointment_id) || ':' ||
           count(DISTINCT visit.visit_id) || ':' ||
           count(DISTINCT submission.form_submission_id) || ':' ||
           count(DISTINCT snapshot.evidence_set_snapshot_id) || ':' ||
           count(DISTINCT rejected_case.review_case_id) || ':' ||
           count(DISTINCT correction.correction_case_id) || ':' ||
           count(DISTINCT approved_case.review_case_id) || ':' ||
           coalesce(max(delivery.status), 'NONE') || ':' ||
           coalesce(max(client_case.status), 'NONE')
      FROM wo_work_order work_order
      LEFT JOIN int_inbound_envelope envelope
        ON envelope.tenant_id = work_order.tenant_id
       AND envelope.project_id = work_order.project_id
       AND envelope.message_type = 'CREATE_WORK_ORDER'
       AND envelope.processing_status = 'COMPLETED'
       AND envelope.result_type = 'WORK_ORDER'
       AND envelope.result_id = work_order.id::text
      LEFT JOIN int_canonical_message canonical
        ON canonical.tenant_id = envelope.tenant_id
       AND canonical.canonical_message_id = envelope.canonical_message_id
       AND canonical.message_type = 'CREATE_WORK_ORDER'
       AND canonical.result_code = 'ACCEPTED'
       AND canonical.result_type = 'WORK_ORDER'
       AND canonical.result_id = work_order.id::text
      LEFT JOIN wfl_workflow_instance workflow
        ON workflow.tenant_id = work_order.tenant_id
       AND workflow.work_order_id = work_order.id
      LEFT JOIN tsk_task task
        ON task.tenant_id = work_order.tenant_id
       AND task.work_order_id = work_order.id
       AND task.task_id = '${inbound_task_id}'
      LEFT JOIN apt_appointment appointment
        ON appointment.tenant_id = task.tenant_id
       AND appointment.task_id = task.task_id
       AND appointment.status = 'COMPLETED'
      LEFT JOIN fld_visit visit
        ON visit.tenant_id = appointment.tenant_id
       AND visit.appointment_id = appointment.appointment_id
       AND visit.status = 'COMPLETED'
      LEFT JOIN frm_form_submission submission
        ON submission.tenant_id = task.tenant_id
       AND submission.task_id = task.task_id
       AND submission.validation_status = 'VALIDATED'
      LEFT JOIN evd_evidence_set_snapshot snapshot
        ON snapshot.tenant_id = task.tenant_id
       AND snapshot.task_id = task.task_id
      LEFT JOIN evd_review_case rejected_case
        ON rejected_case.tenant_id = task.tenant_id
       AND rejected_case.task_id = task.task_id
       AND rejected_case.origin = 'INTERNAL'
       AND rejected_case.status = 'REJECTED'
      LEFT JOIN evd_correction_case correction
        ON correction.tenant_id = rejected_case.tenant_id
       AND correction.source_review_case_id = rejected_case.review_case_id
       AND correction.status = 'CLOSED'
      LEFT JOIN evd_review_case approved_case
        ON approved_case.tenant_id = task.tenant_id
       AND approved_case.task_id = task.task_id
       AND approved_case.origin = 'INTERNAL'
       AND approved_case.status = 'APPROVED'
      LEFT JOIN int_outbound_delivery delivery
        ON delivery.tenant_id = approved_case.tenant_id
       AND delivery.source_review_case_id = approved_case.review_case_id
       AND delivery.status = 'ACKNOWLEDGED'
      LEFT JOIN evd_review_case client_case
        ON client_case.review_case_id = delivery.client_review_case_id
       AND client_case.origin = 'CLIENT'
       AND client_case.status = 'APPROVED'
     WHERE work_order.tenant_id = 'tenant-local'
       AND work_order.external_order_code = '${inbound_order_code}'
     GROUP BY work_order.id, work_order.status
  ")"
  # FULFILLED : Envelope : BYD:INSTALL : WF : Task : formRef : Apt : Visit : Form : Snapshots : REJECTED : CLOSED : APPROVED : Outbound : CLIENT
  [[ "${inbound_state}" == "FULFILLED:1:t:COMPLETED:COMPLETED:admin.pilot-inbound-form:1:1:1:2:1:1:1:ACKNOWLEDGED:APPROVED" \
    || "${inbound_state}" == "FULFILLED:1:true:COMPLETED:COMPLETED:admin.pilot-inbound-form:1:1:1:2:1:1:1:ACKNOWLEDGED:APPROVED" ]] && break
  sleep 1
done
[[ "${inbound_state}" == "FULFILLED:1:t:COMPLETED:COMPLETED:admin.pilot-inbound-form:1:1:1:2:1:1:1:ACKNOWLEDGED:APPROVED" \
  || "${inbound_state}" == "FULFILLED:1:true:COMPLETED:COMPLETED:admin.pilot-inbound-form:1:1:1:2:1:1:1:ACKNOWLEDGED:APPROVED" ]] || {
  echo "Admin 试点入站同单整改补传复审外发贯通不完整: ${inbound_state}" >&2
  exit 1
}

assert_manual_service_assignment "${field_ops_task_id}" "field-ops"
assert_manual_service_assignment "${inbound_task_id}" "inbound"

echo "Admin 试点冒烟通过：真实 Keycloak、Backend、PostgreSQL 与浏览器链路均已验证"
