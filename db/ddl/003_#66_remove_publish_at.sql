-- 003_#66_remove_publish_at.sql
-- 예약 게시 기능을 없애고 publishPosting은 항상 즉시 게시하도록 바꿔서, 더 이상 안 쓰는 publish_at 컬럼을 지움

alter table postings drop column publish_at;
