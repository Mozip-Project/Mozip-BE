# 에러 코드는 도메인_HTTP상태 하나로 두고 상황 구분은 reason으로 한다

- 상태: accepted
- 기록일: 2026-10-28
- 관련: #3, #36, PR #40

## 맥락

벤치마크한 프로젝트들은 `CLUB-001`처럼 하이픈과 일련번호를 썼고, 초기 세팅은 `COMMON_400`처럼 언더스코어와 HTTP 상태였다. 도메인 코드가 생기기 전에 하나로 통일해야 했다. 이후 B 도메인에서 같은 409가 여러 상황(이미 운영진, 처리된 초대, 진행 중 인수)에 걸리는 사례가 바로 나왔다.

## 결정

- 코드는 `도메인_HTTP상태`. 예: `ORG_404`, `MEMBER_409`. 같은 도메인의 같은 상태는 코드 하나
- 같은 코드 안에서 프론트가 분기해야 하면 `reason` enum을 만들고 `extensions.reason`에 enum 이름을 담는다. `message`는 사람용
- 번호 접미사(`MEMBER_409_1`)는 쓰지 않는다
- `addContext`로 붙인 값은 로그에만 남기고 응답에는 넣지 않는다

## 근거

- 프론트가 코드만 보고 HTTP 상태를 알 수 있다. 일련번호는 표를 찾아야 한다
- 코드가 상태별 하나라 표가 작게 유지되고, 상황이 늘어도 reason enum에만 추가하면 된다
- `ErrorCodeRegistry`가 코드 중복을 기동 시 잡으므로 코드 수가 적을수록 충돌이 적다

## 고려한 대안

- 상황마다 코드(`MEMBER_409_ALREADY_MEMBER`): 코드 수가 상황 수만큼 늘고 Swagger 에러 표가 길어진다
- `message`로 분기: 문구가 바뀌면 프론트가 깨진다

## 영향 범위

- `docs/agent/rules/error.md`, `docs/agent/references/error-codes.md`
- `BusinessException(errorCode, reason)` 생성자와 `GraphqlExceptionResolver`의 `reason` 처리 (#36)
- 초기 `AUTH_001`~`AUTH_007`, `USER_001`은 번호 형식으로 남아 있다. 바꾸지 않는다

## 변경 기록

- 2026-10-28: 코드 형식을 언더스코어 `도메인_HTTP상태`로 통일. 같은 상태 여러 개는 생기면 정하기로
- 2026-11-17: B 도메인에서 사례가 생겨 `reason` 방식으로 결정. PR #40에서 구현
