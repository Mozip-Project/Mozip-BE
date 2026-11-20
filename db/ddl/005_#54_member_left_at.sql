-- 005_#54_member_left_at.sql
-- 운영진 내보내기(#54): soft delete 컬럼 left_at. 평가 기록이 members를 참조해 행은 남기고 이 값으로 소속 여부를 가름
-- 소속 unique와 대표자 unique는 소속 중인 행(left_at is null)에만 걸어, 나간 회원을 다시 초대할 수 있게 함

alter table members add column left_at timestamp(6);

alter table members drop constraint members_organization_id_user_id_key;
create unique index uk_members_active_user on members (organization_id, user_id) where left_at is null;

drop index uk_members_representative;
create unique index uk_members_representative on members (organization_id) where role = 'REPRESENTATIVE' and left_at is null;
