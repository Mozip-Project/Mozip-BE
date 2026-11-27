-- 104_attachment_size.sql
-- answer.attachments에서 파일 크기도 내려주기 위해 attachments에 size 컬럼 추가 (FILE만 값 있음, LINK는 null)

alter table attachments add column if not exists size integer;
