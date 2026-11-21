-- 002_#66_posting_status.sql
-- 공고 진행 단계 전환/삭제, 모집 마감 스케줄러를 위한 postings 보강.
-- 모집 마감 스케줄러 조회용 인덱스를 추가함

create index idx_postings_status_recruit_end_at on postings (status, recruit_end_at);
