-- 004_#52_user_last_organization.sql
-- 단체 선택(#52): 운영진 홈 드롭다운에서 마지막으로 고른 단체를 회원에 저장 (User.lastOrganization)

alter table users add column last_organization_id bigint;

alter table users add constraint fk_users_last_organization_id foreign key (last_organization_id) references organizations;
