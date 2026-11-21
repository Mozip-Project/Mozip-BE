# 프론트 API는 GraphQL로, REST는 인증과 헬스 체크에만 쓴다

- 상태: accepted
- 기록일: 2026-10-28
- 관련: #3, #23

## 맥락

처음 세팅은 REST와 `ApiResponse` 래퍼였다. 프론트가 화면마다 단체, 공고, 지원서를 중첩해서 조회해야 해서 REST로는 엔드포인트가 화면 수만큼 늘어날 상황이었다.

## 결정

- 도메인 조회와 변경은 GraphQL 하나의 엔드포인트(`/graphql`)로 제공한다. 스키마는 도메인별 `.graphqls`
- REST는 로그인, 토큰 재발급, 헬스 체크처럼 쿠키와 HTTP 상태 코드가 중요한 것에만 쓴다
- 에러는 두 쪽이 같은 `code`를 쓴다. GraphQL은 `errors[].extensions`에, REST는 `ApiResponse`에 담는다

## 근거

- 프론트가 필요한 필드만 골라 받고 중첩 조회를 한 번에 한다. 백엔드는 `@BatchMapping`으로 N+1만 막으면 된다
- 스키마가 곧 문서라 프론트 codegen과 타입이 자동으로 맞는다
- 인증은 쿠키 설정과 401 응답 처리가 REST 쪽이 단순하다

## 고려한 대안

- REST만: 화면별 조합 엔드포인트가 늘고 Swagger 문서를 계속 맞춰야 한다
- GraphQL만: 로그인 응답에서 쿠키를 심는 처리와 토큰 만료 401 분기가 GraphQL 200 응답 안에서 어색하다

## 영향 범위

- `docs/agent/rules/graphql.md`, `error.md`
- GraphQL 에러의 `errorType`에는 409, 422가 없어 그 외 4xx는 `BAD_REQUEST`로 두고 `code`로 구분한다
- Swagger에는 REST만 뜬다. GraphQL 문서는 GraphiQL과 스키마 설명 문자열

## 변경 기록

- 2026-10-28: REST와 GraphQL 병행 결정, 헬스 체크 Query로 세팅
- 2026-11-17: #23에서 전 도메인 스키마를 GraphQL로 작성. 프론트도 graphql-request로 전환
- 2026-11-20: REST 범위에 운영진의 현재 단체 선택과 전환을 추가 (결정 007)
