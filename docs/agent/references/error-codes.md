# 에러 코드 표

`ErrorCodeRegistry`가 기동 시 모든 `ErrorCode` enum을 스캔하므로 코드가 겹치면 앱이 뜨지 않는다. 코드를 추가하거나 바꾸면 이 표를 같은 PR에서 갱신한다. 형식 규칙은 `../rules/error.md`.

## 공통 (`CommonErrorCode`)

| 코드 | HTTP | 상수 | 언제 |
|---|---|---|---|
| `COMMON_400` | 400 | `INVALID_INPUT` | `@Valid` 실패, 필드 간 규칙 위반 (`reason`으로 구분) |
| `COMMON_401` | 401 | `UNAUTHORIZED` | 로그인 필요 |
| `COMMON_403` | 403 | `FORBIDDEN` | `@PreAuthorize` 거부, 단체 소속 아님 |
| `COMMON_404` | 404 | `NOT_FOUND` | 도메인을 특정할 수 없는 자원 없음 |
| `COMMON_405` | 405 | `METHOD_NOT_ALLOWED` | |
| `COMMON_413` | 413 | `CONTENT_TOO_LARGE` | |
| `COMMON_415` | 415 | `UNSUPPORTED_MEDIA_TYPE` | |
| `COMMON_500` | 500 | `INTERNAL_SERVER_ERROR` | 처리되지 않은 예외 |

## 인증 (`AuthErrorCode`, `TokenErrorCode`)

초기에 만든 번호 접미사 형식이다. 그대로 두고 새 코드는 `도메인_HTTP상태`로 만든다.

| 코드 | HTTP | 상수 | 언제 |
|---|---|---|---|
| `AUTH_001` | 401 | `INVALID_CREDENTIALS` | 휴대폰 번호 또는 비밀번호 불일치 |
| `AUTH_002` | 401 | `EXPIRED_ACCESS_TOKEN` | |
| `AUTH_003` | 401 | `INVALID_ACCESS_TOKEN` | |
| `AUTH_004` | 401 | `INVALID_REFRESH_TOKEN` | 재로그인 필요 |
| `AUTH_005` | 403 | `INACTIVE_USER` | 이용할 수 없는 계정 |
| `AUTH_006` | 503 | `TOKEN_STORE_UNAVAILABLE` | Redis 장애 |
| `AUTH_007` | 409 | `DUPLICATE_REISSUE` | 같은 refresh 토큰으로 중복 재발급 |

## 회원 (`UserErrorCode`)

| 코드 | HTTP | 상수 | 언제 |
|---|---|---|---|
| `USER_001` | 409 | `DUPLICATE_PHONE` | 이미 가입된 휴대폰 번호 |

## 단체 (`OrganizationErrorCode`, PR #40)

| 코드 | HTTP | 상수 | 언제 |
|---|---|---|---|
| `ORG_402` | 402 | `SUBSCRIPTION_REQUIRED` | 이용권 없는 단체의 유료 기능 호출 (A 이용권에서 사용, `publishPosting`에서도 사용, #65) |
| `ORG_404` | 404 | `ORGANIZATION_NOT_FOUND` | 단체 없음. 심사 전이거나 반려된 단체를 소속이 아닌 사람이 상세 조회할 때도 같음 |

## 공고 (`PostingErrorCode`, #65)

| 코드 | HTTP | 상수 | 언제 |
|---|---|---|---|
| `POSTING_404` | 404 | `NOT_FOUND` | 공고 없음. DRAFT인데 그 단체 운영진이 아니면 있어도 이 코드로 숨김 |
| `POSTING_409` | 409 | `CONFLICT` | `reason`으로 구분 (`PostingConflictReason`) |
| `POSTING_400` | 400 | `INVALID_INPUT` | `publishPosting`에서 필수 단계 누락. `fieldErrors`에 어느 단계인지 담김 |

## reason enum

같은 코드 안에서 상황을 나누는 값. 응답의 `extensions.reason`에 enum 이름이 들어간다. `BusinessException(errorCode, reason)`
생성자와 `GraphqlExceptionResolver`의 처리는 결정 003에서 #36 몫으로 적어뒀지만 #36이 아직 안 머지돼서 #65에서 먼저 구현함

| enum | 코드 | 값 |
|---|---|---|
| `OrganizationInputReason` | `COMMON_400` | `COUNCIL_LEVEL_REQUIRED`, `COUNCIL_LEVEL_NOT_ALLOWED`, `SCHOOL_REQUIRED`, `SCHOOL_NOT_ALLOWED`, `SCHOOL_NOT_FOUND` (#37), `FIELDS_REQUIRED`, `FIELDS_NOT_ALLOWED`, `LINK_URL_INVALID` (#38 예정) |
| `PostingConflictReason` | `POSTING_409` | `NOT_DRAFT`(DRAFT 아닌 공고 수정 또는 삭제), `ALREADY_PUBLISHED`(이미 게시된 공고 재게시), `INVALID_STATUS_TRANSITION`(정해진 다음 단계가 아님, #66), `DOC_RESULT_NOT_CONFIRMED`(서류 결과 미확정, #66), `FINAL_RESULT_NOT_CONFIRMED`(최종 결과 미확정, #66) |

## fieldErrors를 응답에 담는 BusinessException

Bean Validation(`@Valid`, `ConstraintViolationException`)이 아닌 서비스 레벨 검증도 `extensions.fieldErrors`로
필드별 메시지를 보내야 할 때는 `BusinessException.withFieldErrors(List<FieldErrorResponse>)`를 쓴다 (#65에서
`FieldErrorResponse`를 api에서 common으로 옮기고 추가함). `addContext`와 달리 응답에 실제로 포함된다.
