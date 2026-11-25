-- 090_doc_evaluation.sql
-- 서류 평가 작성에 필요한 상태(DRAFT/SAVED), 메모, 문항별 점수 컬럼을 doc_evaluations에 추가.
-- dev는 ddl-auto: update라 컬럼이 먼저 생길 수 있어 add column은 if not exists로 씀

alter table doc_evaluations add column if not exists status varchar(30) not null default 'DRAFT';
alter table doc_evaluations add column if not exists memo varchar(300);
alter table doc_evaluations add column if not exists scores jsonb not null default '[]';

-- 컬럼 추가 전에 제출된 평가는 submitted_at이 있으므로 SAVED로 맞춤
update doc_evaluations set status = 'SAVED' where submitted_at is not null and status = 'DRAFT';

-- JPA로 표현할 수 없어 DDL에만 둠
alter table doc_evaluations drop constraint if exists doc_evaluations_status_check;
alter table doc_evaluations add constraint doc_evaluations_status_check check (status in ('DRAFT', 'SAVED'));
