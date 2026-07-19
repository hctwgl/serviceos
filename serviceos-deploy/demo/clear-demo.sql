-- 仅删除演示标记数据（不动结构、不动非演示业务）
\set ON_ERROR_STOP on

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
