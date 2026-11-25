-- 099_remove_interview_method.sql
-- 면접 방식(온라인/오프라인) 구분을 더 이상 안 써서 postings.interview_method 컬럼 삭제 (#99)

alter table postings drop column if exists interview_method;
