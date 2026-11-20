-- 005_#54_member_left_at.sql
-- 운영진 내보내기(#54): soft delete 컬럼 left_at. 평가 기록이 members를 참조해 행은 남기고 이 값으로 소속 여부를 가름
-- 소속 unique와 대표자 unique는 소속 중인 행(left_at is null)에만 걸어, 나간 회원을 다시 초대할 수 있게 함
-- dev/prod는 ddl-auto: update라 컬럼이 먼저 생겼을 수 있고, 기존 소속 unique는 Hibernate가 만든 UK 해시 이름일 수 있어 이름 대신 컬럼으로 찾아 지움

alter table members add column if not exists left_at timestamp(6);

do $$
declare
    target text;
begin
    for target in
        select c.conname
        from pg_constraint c
        where c.conrelid = 'members'::regclass
          and c.contype = 'u'
          and (select array_agg(a.attname::text order by a.attname)
               from unnest(c.conkey) k join pg_attribute a on a.attrelid = c.conrelid and a.attnum = k)
              = array['organization_id', 'user_id']
    loop
        execute format('alter table members drop constraint %I', target);
    end loop;
end $$;

create unique index if not exists uk_members_active_user on members (organization_id, user_id) where left_at is null;

drop index if exists uk_members_representative;
create unique index uk_members_representative on members (organization_id) where role = 'REPRESENTATIVE' and left_at is null;
