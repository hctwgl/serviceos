-- 仅删除演示标记数据（不动结构、不动非演示业务）
\set ON_ERROR_STOP on

-- 0) 演示审核/整改与任务链路（须先于工单 / 网点夹具删除）
DELETE FROM evd_correction_resubmission
 WHERE correction_case_id::text LIKE 'd3500000-29%'
    OR correction_case_id IN (
        SELECT correction_case_id FROM evd_correction_case
         WHERE source_review_case_id::text LIKE 'd3500000-2920-%'
    );

DELETE FROM evd_correction_case
 WHERE correction_case_id::text LIKE 'd3500000-29%'
    OR source_review_case_id::text LIKE 'd3500000-2920-%'
    OR task_id::text LIKE 'd3500000-2000-%';

-- review_case 触发器禁止 DELETE；演示清理在复制角色下绕过（仅本地演示脚本）
SET session_replication_role = replica;
DELETE FROM evd_review_decision
 WHERE review_case_id::text LIKE 'd3500000-2920-%';

DELETE FROM evd_review_case
 WHERE review_case_id::text LIKE 'd3500000-2920-%'
    OR task_id::text LIKE 'd3500000-2000-%';
SET session_replication_role = DEFAULT;

SET session_replication_role = replica;
DELETE FROM evd_evidence_set_snapshot
 WHERE evidence_set_snapshot_id::text LIKE 'd3500000-2910-%'
    OR task_id::text LIKE 'd3500000-2000-%';

DELETE FROM evd_task_evidence_resolution
 WHERE resolution_id::text LIKE 'd3500000-2900-%'
    OR task_id::text LIKE 'd3500000-2000-%';
SET session_replication_role = DEFAULT;

DELETE FROM dsp_capacity_reservation
 WHERE capacity_reservation_id::text LIKE 'd3500000-2420-%'
    OR capacity_reservation_id::text LIKE 'd3500000-2520-%';

DELETE FROM dsp_service_assignment
 WHERE service_assignment_id::text LIKE 'd3500000-2400-%'
    OR service_assignment_id::text LIKE 'd3500000-2500-%';

DELETE FROM dsp_capacity_counter
 WHERE capacity_counter_id::text LIKE 'd3500000-2700-%';

-- SLA 实例触发器禁止 DELETE；演示清理在复制角色下绕过（仅本地演示脚本）
SET session_replication_role = replica;
DELETE FROM sla_instance
 WHERE sla_instance_id::text LIKE 'd3500000-2600-%';
SET session_replication_role = DEFAULT;

DELETE FROM ops_operational_exception
 WHERE exception_id::text LIKE 'd3500000-2800-%';

DELETE FROM apt_contact_attempt
 WHERE contact_attempt_id::text LIKE 'd3500000-2b00-%'
    OR task_id::text LIKE 'd3500000-2000-%';

DELETE FROM apt_appointment_status_history
 WHERE history_id::text LIKE 'd3500000-2a20-%'
    OR appointment_id::text LIKE 'd3500000-2a00-%';

DELETE FROM apt_appointment_revision
 WHERE revision_id::text LIKE 'd3500000-2a10-%'
    OR appointment_id::text LIKE 'd3500000-2a00-%';

DELETE FROM apt_appointment
 WHERE appointment_id::text LIKE 'd3500000-2a00-%';

-- 驳回开整改时可能创建 evidence.correction 任务；按演示工单清理附属 HUMAN 任务
DELETE FROM tsk_task
 WHERE work_order_id::text LIKE 'd3500000-0000-%'
   AND task_id::text NOT LIKE 'd3500000-2000-%';

DELETE FROM tsk_task
 WHERE task_id::text LIKE 'd3500000-2000-%';

DELETE FROM wfl_stage_instance
 WHERE stage_instance_id::text LIKE 'd3500000-2200-%';

DELETE FROM wfl_workflow_instance
 WHERE workflow_instance_id::text LIKE 'd3500000-2100-%';

-- 1) 演示工单
DELETE FROM wo_work_order
 WHERE tenant_id = 'tenant-local'
   AND (
     external_order_code LIKE 'WO-DEMO-%'
     OR id::text LIKE 'd3500000-0000-%'
   );

-- 2) 演示 Portal 授权与角色
DELETE FROM auth_role_grant
 WHERE grant_id IN (
    'd3500000-1000-4000-8000-00000000000b',
    'd3500000-1000-4000-8000-00000000000c'
 );

DELETE FROM auth_role_capability
 WHERE role_id IN (
    'd3500000-1000-4000-8000-000000000009',
    'd3500000-1000-4000-8000-00000000000a'
 );

DELETE FROM auth_role
 WHERE role_id IN (
    'd3500000-1000-4000-8000-000000000009',
    'd3500000-1000-4000-8000-00000000000a'
 );

-- 3) 演示网点成员 / 师傅 / 资质
DELETE FROM net_technician_qualification
 WHERE qualification_id::text LIKE 'd3500000-1000-%';

DELETE FROM net_network_technician_membership
 WHERE membership_id::text LIKE 'd3500000-1000-%';

DELETE FROM net_technician_profile
 WHERE technician_profile_id::text LIKE 'd3500000-1000-%';

DELETE FROM net_network_membership
 WHERE membership_id::text LIKE 'd3500000-1000-%';

DELETE FROM prj_project_network
 WHERE project_network_id::text LIKE 'd3500000-1000-%';

DELETE FROM net_service_network
 WHERE service_network_id::text LIKE 'd3500000-1000-%';

DELETE FROM net_partner_organization
 WHERE partner_organization_id::text LIKE 'd3500000-1000-%';

-- 4) 演示 Persona（保留 developer 的 INTERNAL_EMPLOYEE）
DELETE FROM idn_principal_persona
 WHERE persona_id IN (
    'd3500000-1000-4000-8000-000000000007',
    'd3500000-1000-4000-8000-000000000008'
 );

-- 5) 仅演示用、无 Keycloak 绑定的李师傅主体
DELETE FROM idn_person_profile
 WHERE principal_id = 'd3500000-1000-4000-8000-000000000010';

DELETE FROM idn_security_principal
 WHERE principal_id = 'd3500000-1000-4000-8000-000000000010';
