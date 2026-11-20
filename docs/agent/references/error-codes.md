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
| `USER_404` | 404 | `NOT_FOUND` | 회원 없음 (알림 설정 변경 시 사용, #48) |

## 단체 (`OrganizationErrorCode`, PR #40)

| 코드 | HTTP | 상수 | 언제 |
|---|---|---|---|
| `ORG_402` | 402 | `SUBSCRIPTION_REQUIRED` | 이용권 없는 단체의 유료 기능 호출 (A 이용권에서 사용) |
| `ORG_404` | 404 | `ORGANIZATION_NOT_FOUND` | 단체 없음 |

## 알림 (`NotificationErrorCode`, #48)

| 코드 | HTTP | 상수 | 언제 |
|---|---|---|---|
| `NOTIFICATION_403` | 403 | `FORBIDDEN` | `markNotificationsRead`에 본인 알림이 아닌 id가 섞여 있음 |

## reason enum

같은 코드 안에서 상황을 나누는 값. 응답의 `extensions.reason`에 enum 이름이 들어간다.

| enum | 코드 | 값 |
|---|---|---|
| `OrganizationInputReason` | `COMMON_400` | `COUNCIL_LEVEL_REQUIRED`, `COUNCIL_LEVEL_NOT_ALLOWED`, `SCHOOL_REQUIRED`, `SCHOOL_NOT_ALLOWED`, `SCHOOL_NOT_FOUND` (#37), `FIELDS_REQUIRED`, `FIELDS_NOT_ALLOWED`, `LINK_URL_INVALID` (#38 예정) |
