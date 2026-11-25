-- 070_organization_reject_reason.sql
-- 단체 등록 심사(#70): 미승인 사유 컬럼 추가. REJECTED일 때만 값이 있음
-- 컬럼은 dev/prod에 이미 있을 수 있어 if not exists로 추가

alter table organizations add column if not exists reject_reason varchar(500);
