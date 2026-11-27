-- 107_interview_timetable.sql
-- 면접 칸에 장소를 적을 수 있게 interviews.location 추가, 공고별 타임테이블 조회용 인덱스 (#107)

alter table interviews add column if not exists location varchar(100);

create index if not exists idx_interviews_posting_id on interviews (posting_id);
