-- 037_organization_create.sql
-- 단체 생성(#37): 학생회 세부 구분, 증빙 컬럼 추가, 단체당 대표자 1명 부분 unique index (DDL에만 있음)
-- 컬럼은 dev/prod에 이미 있을 수 있어 if not exists로 추가

alter table organizations add column if not exists council_level varchar(30) check ((council_level in ('UNIVERSITY','COLLEGE','DEPARTMENT')));
alter table organizations add column if not exists proof_link varchar(500);
alter table organizations add column if not exists proof_file_url varchar(500);

create unique index if not exists uk_members_representative on members (organization_id) where role = 'REPRESENTATIVE';
