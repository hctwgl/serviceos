import { createHash, randomUUID } from 'node:crypto'

const baseUrl = process.env.SERVICEOS_PRODUCT_API_URL ?? 'http://localhost:8080/api/v1'
const accessToken = process.env.SERVICEOS_PRODUCT_ACCESS_TOKEN
if (!accessToken) throw new Error('缺少 SERVICEOS_PRODUCT_ACCESS_TOKEN')

let sequence = 0
const key = (purpose) => `product-data-${purpose}-${++sequence}`

async function request(path, { method = 'GET', body, idempotencyKey, headers = {} } = {}) {
  const response = await fetch(`${baseUrl}${path}`, {
    method,
    headers: {
      Authorization: `Bearer ${accessToken}`,
      'Content-Type': 'application/json',
      ...(idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : {}),
      ...headers,
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  const text = await response.text()
  const result = text ? JSON.parse(text) : null
  if (!response.ok) {
    throw new Error(`${method} ${path} 失败（HTTP ${response.status}）：${text}`)
  }
  return { body: result, etag: response.headers.get('etag') }
}

async function registerPrincipal(displayName, employeeNumber, personaType) {
  return (await request('/security-principals', {
    method: 'POST',
    idempotencyKey: key(`principal-${employeeNumber}`),
    body: { displayName, employeeNumber, personaType },
  })).body
}

await request('/project-clients', {
  method: 'POST',
  idempotencyKey: key('client-byd'),
  body: { clientCode: 'BYD', displayName: '比亚迪' },
})
await request('/project-clients/BYD/brands', {
  method: 'POST',
  idempotencyKey: key('brand-ocean'),
  body: { brandCode: 'BYD_OCEAN', displayName: '比亚迪海洋', sortOrder: 10 },
})

const staff = {}
for (const item of [
  ['customerManager', '周雨桐', 'SO-CS-018', 'INTERNAL_EMPLOYEE'],
  ['projectManager', '陈昊', 'SO-PM-006', 'INTERNAL_EMPLOYEE'],
  ['projectAssistant', '林晓雯', 'SO-PA-012', 'INTERNAL_EMPLOYEE'],
  ['reviewer', '沈清妍', 'SO-QA-008', 'INTERNAL_EMPLOYEE'],
  ['technicianA', '李建国', 'SO-TECH-031', 'TECHNICIAN'],
  ['technicianB', '赵海峰', 'SO-TECH-027', 'TECHNICIAN'],
  ['technicianC', '周志强', 'SO-TECH-044', 'TECHNICIAN'],
]) {
  staff[item[0]] = await registerPrincipal(item[1], item[2], item[3])
}

// 企业组织与人员任职必须通过正式身份治理接口建立，不能只在用户目录中留下“无任职”的占位状态。
// 组织结构是人员归属事实，不承担角色授权；后续 RoleGrant 仍按租户、项目和区域范围独立配置。
const organization = (await request('/organizations', {
  method: 'POST', idempotencyKey: key('organization-serviceos-east-china'),
  body: {
    code: 'SERVICEOS-EAST-CHINA',
    name: 'ServiceOS 华东运营中心',
    authorityMode: 'LOCAL',
  },
})).body

async function createOrganizationUnit(unitCode, unitName) {
  const current = (await request(`/organizations/${organization.id}`)).body.organization
  return (await request(`/organizations/${organization.id}/units`, {
    method: 'POST', idempotencyKey: key(`org-unit-${unitCode}`),
    headers: { 'If-Match': `"${current.version}"` },
    body: { parentUnitId: null, unitCode, unitName },
  })).body
}

const customerServiceUnit = await createOrganizationUnit('CUSTOMER-SERVICE', '客户服务部')
const projectDeliveryUnit = await createOrganizationUnit('PROJECT-DELIVERY', '项目履约部')
const qualityUnit = await createOrganizationUnit('QUALITY-ASSURANCE', '质量审核部')

const membershipValidFrom = new Date(Date.now() - 86_400_000).toISOString()
for (const [staffKey, unit, membershipType] of [
  ['customerManager', customerServiceUnit, 'MANAGER'],
  ['projectManager', projectDeliveryUnit, 'MANAGER'],
  ['projectAssistant', projectDeliveryUnit, 'PRIMARY'],
  ['reviewer', qualityUnit, 'PRIMARY'],
]) {
  await request(`/organizations/${organization.id}/memberships`, {
    method: 'POST', idempotencyKey: key(`org-membership-${staffKey}`),
    body: {
      unitId: unit.id,
      principalId: staff[staffKey].id,
      membershipType,
      validFrom: membershipValidFrom,
    },
  })
}

const partner = (await request('/partner-organizations', {
  method: 'POST', idempotencyKey: key('partner-jinan'),
  body: { code: 'JINAN-HENGTONG', name: '济南恒通新能源服务有限公司' },
})).body
const network = (await request('/service-networks', {
  method: 'POST', idempotencyKey: key('network-jinan'),
  body: {
    partnerOrganizationId: partner.id,
    networkCode: 'JINAN-LIXIA',
    networkName: '济南历下服务中心',
  },
})).body

const technicianProfiles = []
for (const [index, staffKey] of ['technicianA', 'technicianB', 'technicianC'].entries()) {
  const profile = (await request('/technician-profiles', {
    method: 'POST', idempotencyKey: key(`technician-${index + 1}`),
    body: {
      principalId: staff[staffKey].id,
      displayName: staff[staffKey].displayName,
      supportedClientKinds: ['TECHNICIAN_IOS'],
    },
  })).body
  technicianProfiles.push(profile)
  await request('/network-technician-memberships', {
    method: 'POST', idempotencyKey: key(`membership-${index + 1}`),
    body: {
      networkId: network.id,
      technicianProfileId: profile.id,
      validFrom: new Date(Date.now() - 86_400_000).toISOString(),
    },
  })
  const qualification = (await request('/technician-qualifications', {
    method: 'POST', idempotencyKey: key(`qualification-${index + 1}`),
    body: {
      technicianProfileId: profile.id,
      qualificationCode: 'HOME_CHARGING_INSTALLATION',
      validFrom: new Date(Date.now() - 86_400_000).toISOString(),
      validTo: new Date(Date.now() + 365 * 86_400_000).toISOString(),
    },
  })).body
  await request(`/technician-qualifications/${qualification.id}:decide`, {
    method: 'POST', idempotencyKey: key(`qualification-decision-${index + 1}`),
    body: { decision: 'APPROVED', reason: '本地产品场景固定资质' },
    // 资格刚创建时聚合版本固定为 1；服务端继续校验并发版本。
    headers: { 'If-Match': '"1"' },
  })
}

const project = (await request('/projects', {
  method: 'POST', idempotencyKey: key('project-byd-ocean'),
  body: {
    code: 'BYD-OCEAN-SD-PILOT',
    clientId: 'BYD',
    name: '比亚迪山东家充项目',
    startsOn: new Date().toISOString().slice(0, 10),
    endsOn: null,
    regionCodes: ['370000', '370100', '370102'],
    networkIds: [network.id],
  },
})).body
await request(`/projects/${project.id}:activate`, {
  method: 'POST',
  idempotencyKey: key('project-activate'),
  headers: { 'If-Match': `"${project.version}"` },
})

// 项目团队与区域分工是项目日常运营主数据，不进入履约配置版本。全部通过正式命令建立，
// 后续新工单按“项目 + 标准行政区”匹配并冻结客服经理、项目经理和项目助理。
for (const staffKey of ['customerManager', 'projectManager', 'projectAssistant']) {
  await request(`/projects/${project.id}/team-members`, {
    method: 'POST',
    idempotencyKey: key(`project-member-${staffKey}`),
    body: { principalId: staff[staffKey].id },
  })
}
for (const assignment of [
  {
    key: 'customer-province', regionCode: '370000', positionCode: 'CUSTOMER_SERVICE_MANAGER',
    principalId: staff.customerManager.id, allowInheritance: true,
    reason: '周雨桐负责山东省比亚迪家充客户服务协调，下级区域未配置时允许继承。',
  },
  {
    key: 'customer-lixia', regionCode: '370102', positionCode: 'CUSTOMER_SERVICE_MANAGER',
    principalId: staff.customerManager.id, allowInheritance: false,
    reason: '济南历下区试点工单由周雨桐直接负责客户沟通。',
  },
  {
    key: 'manager-jinan', regionCode: '370100', positionCode: 'PROJECT_MANAGER',
    principalId: staff.projectManager.id, allowInheritance: true,
    reason: '陈昊负责济南市比亚迪家充项目履约结果与重大异常协调。',
  },
  {
    key: 'assistant-jinan', regionCode: '370100', positionCode: 'PROJECT_ASSISTANT',
    principalId: staff.projectAssistant.id, allowInheritance: true,
    reason: '林晓雯负责济南市项目资料、履约进度和跨方协同。',
  },
]) {
  await request(`/projects/${project.id}/region-personnel:assign`, {
    method: 'POST',
    idempotencyKey: key(`project-region-${assignment.key}`),
    body: {
      regionCode: assignment.regionCode,
      positionCode: assignment.positionCode,
      principalId: assignment.principalId,
      expectedCurrentAssignmentId: null,
      allowInheritance: assignment.allowInheritance,
      reason: assignment.reason,
    },
  })
}

const configurationFoundation = (await request(
  `/product-development/projects/${project.id}/configuration-foundation`,
  { method: 'POST' },
)).body

const profile = (await request(`/projects/${project.id}/fulfillment-profiles`, {
  method: 'POST', idempotencyKey: key('fulfillment-profile'),
  body: {
    serviceProductCode: 'HOME_CHARGING_SURVEY_INSTALL',
    profileName: '家充勘测安装标准履约',
    description: '覆盖预约、勘测、安装、资料审核与车企回传的本地产品场景。',
    templateCode: 'HOME_CHARGING_SURVEY_INSTALL',
    copyFromProfileId: null,
  },
})).body
const profileDraft = (await request(
  `/projects/${project.id}/fulfillment-profiles/${profile.profileId}/draft`,
)).body
// 让履约方案文档与运行期配置包保持一致：勘测阶段绑定表单与资料，安装阶段绑定完工资料。
// 文档中的引用只影响方案可读性与运行说明书，运行期任务模板仍以配置包内 WORKFLOW/FORM/EVIDENCE 资产为准。
const documentWithRefs = {
  ...profileDraft.document,
  stages: (profileDraft.document.stages ?? []).map((stage) => {
    if (stage.stageCode === 'SURVEY') {
      return {
        ...stage,
        slaRef: 'platform.home-charging.task-elapsed',
        formRefs: ['product.byd-ocean.survey-form'],
        evidenceRefs: ['product.byd-ocean.survey-evidence'],
      }
    }
    if (stage.stageCode === 'INSTALLATION') {
      return {
        ...stage,
        slaRef: 'platform.home-charging.task-elapsed',
        evidenceRefs: ['product.byd-ocean.install-evidence'],
      }
    }
    return stage
  }),
}
await request(`/projects/${project.id}/fulfillment-profiles/${profile.profileId}/draft`, {
  method: 'PUT',
  idempotencyKey: key('fulfillment-bind-foundation'),
  headers: { 'If-Match': `"${profileDraft.aggregateVersion}"` },
  body: {
    profileName: profile.profileName,
    description: profile.description,
    document: documentWithRefs,
    workflowAssetVersionId: configurationFoundation.workflowAssetVersionId,
    sourceBundleId: configurationFoundation.sourceBundleId,
  },
})
const validation = (await request(`/projects/${project.id}/fulfillment-profiles/${profile.profileId}:validate`, {
  method: 'POST', idempotencyKey: key('fulfillment-validate'),
})).body
const blocking = validation.filter((item) => item.severity === 'ERROR')
if (blocking.length) {
  throw new Error(`履约配置校验失败：${JSON.stringify(blocking)}`)
}
// 校验会更新草稿校验事实，发布必须读取服务端最新并发版本，不能沿用创建时版本。
const validatedProfile = (await request(
  `/projects/${project.id}/fulfillment-profiles/${profile.profileId}`,
)).body
await request(`/projects/${project.id}/fulfillment-profiles/${profile.profileId}:publish`, {
  method: 'POST',
  idempotencyKey: key('fulfillment-publish'),
  body: { effectiveFrom: new Date().toISOString(), publishNote: '本地产品场景初始化' },
  headers: { 'If-Match': `"${validatedProfile.aggregateVersion}"` },
})

function signByd(parameters, nonce, currentDate) {
  const values = Object.entries(parameters)
    .filter(([, value]) => value !== null && value !== undefined)
    .sort(([left], [right]) => (left < right ? -1 : left > right ? 1 : 0))
    .map(([name, value]) => `${name}=${String(value)}`)
    .join('&')
  return createHash('sha256')
    .update(`local-byd-app-secret-change-me&${nonce}&${currentDate}&${values}`, 'utf8')
    .digest('hex')
}

async function createInstallOrder(index, customerName, districtCode, districtName) {
  const suffix = String(index).padStart(3, '0')
  const parameters = {
    orderCode: `BYD20260722${suffix}`,
    contactName: customerName,
    contactMobile: `13800001${String(index).padStart(3, '0')}`,
    contactAddress: `山东省济南市${districtName}经十路${100 + index}号`,
    provinceCode: '370000', provinceName: '山东省',
    cityCode: '370100', cityName: '济南市',
    areaCode: districtCode, areaName: districtName,
    wallboxName: '比亚迪 7kW 交流充电桩', wallboxPower: '7kW', bringWallbox: '1',
    dispatchTime: `2026-07-${String(22 + (index % 5)).padStart(2, '0')}T09:00:00`,
    carOwnerType: '1', type: '1', carBrand: '40', carSeries: '海豹', carModel: '海豹 06 DM-i',
    vin: `LGXCE6CB${String(100000000 + index).slice(-9)}`,
    dealerName: '比亚迪汽车济南经销商', rightCode: `RIGHT-${suffix}`,
    orderAmount: 1280 + index * 20, source: '1', channel: 'CPIM',
  }
  const currentDate = new Date().toISOString().slice(0, 10)
  const nonce = randomUUID().replaceAll('-', '')
  const response = await fetch(`${baseUrl}/integrations/byd/cpim/v7.3.1/install-orders`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      APP_KEY: 'local-byd-app-key', Nonce: nonce, Cur_Time: currentDate,
      Sign: signByd(parameters, nonce, currentDate),
    },
    body: JSON.stringify(parameters),
  })
  const result = await response.json()
  if (!response.ok || result.success !== true) {
    throw new Error(`创建产品工单 ${parameters.orderCode} 失败：${JSON.stringify(result)}`)
  }
}

const customers = [
  ['王先生', '370102', '历下区'], ['李女士', '370103', '市中区'],
  ['周先生', '370104', '槐荫区'], ['赵女士', '370105', '天桥区'],
  ['孙先生', '370112', '历城区'], ['陈女士', '370114', '章丘区'],
  ['刘先生', '370102', '历下区'], ['张女士', '370103', '市中区'],
]
for (const [index, customer] of customers.entries()) {
  await createInstallOrder(index + 1, ...customer)
}

// 先让入站工单在“尚无可用产能”的真实状态下进入人工派单任务，
// 再通过正式命令登记覆盖和产能。这样人工黄金链路有可解释候选，也不会被自动派单抢先处理。
await new Promise((resolve) => setTimeout(resolve, 2000))
await request(`/service-networks/${network.id}/coverages`, {
  method: 'POST', idempotencyKey: key('network-coverage-jinan'),
  body: {
    brandCode: 'BYD_OCEAN',
    businessType: 'HOME_CHARGING_SURVEY_INSTALL',
    regionCode: '370100',
    validFrom: new Date(Date.now() - 86_400_000).toISOString(),
  },
})
await request('/dispatch/capacities', {
  method: 'POST', idempotencyKey: key('network-capacity-jinan'),
  body: {
    responsibilityLevel: 'NETWORK',
    assigneeId: network.id,
    businessType: 'HOME_CHARGING_SURVEY_INSTALL',
    maxUnits: 30,
    expectedVersion: 0,
  },
})
for (const [index, technicianProfile] of technicianProfiles.entries()) {
  await request('/dispatch/capacities', {
    method: 'POST', idempotencyKey: key(`technician-capacity-${index + 1}`),
    body: {
      responsibilityLevel: 'TECHNICIAN',
      assigneeId: technicianProfile.id,
      businessType: 'HOME_CHARGING_SURVEY_INSTALL',
      maxUnits: index === 0 ? 8 : 6,
      expectedVersion: 0,
    },
  })
}

console.log(`已通过正式业务接口创建项目：${project.name}`)
console.log('已配置项目团队：周雨桐（客服经理）、陈昊（项目经理）、林晓雯（项目助理）')
console.log(`已建立企业组织：${organization.name}（客户服务部、项目履约部、质量审核部）`)
console.log(`已创建服务网点：${network.networkName}`)
console.log(`已登记 ${Object.keys(staff).length} 名项目人员和师傅，并创建 ${customers.length} 张真实工单。`)
