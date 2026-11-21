-- 005_#54_member_left_at.sql
-- 운영진 내보내기(#54): soft delete 컬럼 left_at 추가, 소속/대표자 unique를 소속 중인 행에만 걸도록 바꿈
-- 기존 소속 unique는 환경마다 이름이 다를 수 있어 컬럼으로 찾아 지움

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
