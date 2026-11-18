-- 002_#37_organization_create.sql
-- 단체 생성(#37): 학생회 세부 구분, 증빙 컬럼 추가. 대표자는 단체당 1명 (부분 unique index, JPA로 표현할 수 없어 DDL에만)
-- 순서 2(운영진 내보내기)에서 left_at이 생기면 index 조건에 and left_at is null 추가

alter table organizations add column council_level varchar(30) check ((council_level in ('UNIVERSITY','COLLEGE','DEPARTMENT')));
alter table organizations add column proof_link varchar(500);
alter table organizations add column proof_file_url varchar(500);

create unique index uk_members_representative on members (organization_id) where role = 'REPRESENTATIVE';
