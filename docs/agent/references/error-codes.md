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

## 파일 (`FileErrorCode`)

| 코드 | HTTP | 상수 | 언제 |
|---|---|---|---|
| `FILE_413` | 413 | `FILE_TOO_LARGE` | presigned URL 발급 시 신고한 size가 purpose별 허용 용량 초과 |
| `FILE_415` | 415 | `UNSUPPORTED_FILE_TYPE` | presigned URL 발급 시 contentType이 purpose가 허용하지 않는 타입 |

## 단체 (`OrganizationErrorCode`, PR #40)

| 코드 | HTTP | 상수 | 언제 |
|---|---|---|---|
| `ORG_402` | 402 | `SUBSCRIPTION_REQUIRED` | 이용권 없는 단체의 유료 기능 호출 (A 이용권에서 사용, `publishPosting`에서도 사용, #65) |
| `ORG_404` | 404 | `ORGANIZATION_NOT_FOUND` | 단체 없음. 심사 전이거나 반려된 단체를 소속이 아닌 사람이 상세 조회할 때도 같음 |

## 운영진 (`MemberErrorCode`, PR #58, #60, #61)

| 코드 | HTTP | 상수 | 언제 |
|---|---|---|---|
| `MEMBER_404` | 404 | `MEMBER_NOT_FOUND` | 운영진 없음, 이미 내보낸 운영진, 없는 초대 코드나 대표자 인수 요청. 그 단체 소속이 아닌 사람이 `removeMember`를 부르면 있어도 같음 |
| `MEMBER_409` | 409 | `MEMBER_CONFLICT` | 지금 운영진 상태로는 처리할 수 없음 (`MemberReason`으로 구분) |
| `MEMBER_410` | 410 | `INVITATION_EXPIRED` | 만료된 개인 초대나 공용 링크 |

## 팀 (`TeamErrorCode`, PR #60)

| 코드 | HTTP | 상수 | 언제 |
|---|---|---|---|
| `TEAM_409` | 409 | `TEAM_IN_USE` | 공고 포지션이 쓰는 팀을 삭제하려 함 |

## 알림 (`NotificationErrorCode`, #48)

| 코드 | HTTP | 상수 | 언제 |
|---|---|---|---|
| `NOTIFICATION_403` | 403 | `FORBIDDEN` | `markNotificationsRead`에 본인 알림이 아닌 id가 섞여 있음 |

## 공고 (`PostingErrorCode`, #65)

| 코드 | HTTP | 상수 | 언제 |
|---|---|---|---|
| `POSTING_404` | 404 | `NOT_FOUND` | 공고 없음. DRAFT인데 그 단체 운영진이 아니면 있어도 이 코드로 숨김 |
| `POSTING_409` | 409 | `CONFLICT` | `reason`으로 구분 (`PostingConflictReason`) |
| `POSTING_400` | 400 | `INVALID_INPUT` | `publishPosting`에서 필수 단계 누락. `fieldErrors`에 어느 단계인지 담김 |

## 지원서 (`ApplicationErrorCode`, #69)

| 코드 | HTTP | 상수 | 언제 |
|---|---|---|---|
| `APPLICATION_403` | 403 | `FORBIDDEN` | `reason`으로 구분 (`ApplicationForbiddenReason`) |
| `APPLICATION_404` | 404 | `NOT_FOUND` | 지원서 없음 |
| `APPLICATION_409` | 409 | `CONFLICT` | `reason`으로 구분 (`ApplicationConflictReason`) |
| `APPLICATION_400` | 400 | `INVALID_INPUT` | `submitApplication`에서 필수 문항 누락·글자 수·선택지·첨부 개수 위반. `fieldErrors`에 questionId별 메시지가 담김 |

## 학교 인증 (`SchoolErrorCode`, #76)

| 코드 | HTTP | 상수 | 언제 |
|---|---|---|---|
| `SCHOOL_404` | 404 | `NOT_FOUND` | 메일 도메인이나 schoolId로 학교를 못 찾음 |
| `SCHOOL_400` | 400 | `INVALID_INPUT` | `/auth/signup`(`verifyForSignup`) 인증번호 불일치/만료. `reason`으로 구분 (`SchoolInputReason`). `verifySchoolEmail`은 같은 상황에서 에러 대신 `false`를 반환함 |
| `SCHOOL_409` | 409 | `CONFLICT` | `reason`으로 구분 (`SchoolConflictReason`) |
| `SCHOOL_429` | 429 | `TOO_MANY_REQUESTS` | `requestSchoolEmailVerification`을 60초 안에 다시 요청 |

## 메일 (`MailErrorCode`, #76)

| 코드 | HTTP | 상수 | 언제 |
|---|---|---|---|
| `MAIL_503` | 503 | `SEND_FAILED` | `MailTaskPublisher`가 worker에 발송 작업을 못 올림, 또는 worker의 `SesMailSender`가 SES 호출에 실패(이 경우는 로그로만 남고 호출자에게는 안 전달됨) |

## reason enum

같은 코드 안에서 상황을 나누는 값. 응답의 `extensions.reason`에 enum 이름이 들어간다. `BusinessException(errorCode, reason)`
생성자와 `GraphqlExceptionResolver`의 처리는 결정 003에서 #36 몫으로 적어뒀지만 #36이 아직 안 머지돼서 #65에서 먼저 구현함

| enum | 코드 | 값 |
|---|---|---|
| `OrganizationInputReason` | `COMMON_400` | `COUNCIL_LEVEL_REQUIRED`, `COUNCIL_LEVEL_NOT_ALLOWED`, `SCHOOL_REQUIRED`, `SCHOOL_NOT_ALLOWED`, `SCHOOL_NOT_FOUND` (#37), `FIELDS_REQUIRED`, `FIELDS_NOT_ALLOWED`, `LINK_URL_INVALID` (#38) |
| `MemberReason` | `MEMBER_409` | `REPRESENTATIVE` (#58), `ALREADY_MEMBER`, `ALREADY_INVITED`, `ALREADY_HANDLED` (#60), `TRANSFER_IN_PROGRESS` (#61) |
| `MemberInputReason` | `COMMON_400` | `CONTACT_INVALID`, `NO_TEAM_ORGANIZATION`, `TEAM_NOT_FOUND`, `TEAM_NAME_INVALID`, `TEAM_DUPLICATED` (#60), `TRANSFER_TO_SELF` (#61) |
| `PostingConflictReason` | `POSTING_409` | `NOT_DRAFT`(DRAFT 아닌 공고 수정 또는 삭제), `ALREADY_PUBLISHED`(이미 게시된 공고 재게시), `INVALID_STATUS_TRANSITION`(정해진 다음 단계가 아님, #66), `DOC_RESULT_NOT_CONFIRMED`(서류 결과 미확정, #66), `FINAL_RESULT_NOT_CONFIRMED`(최종 결과 미확정, #66), `NOT_RECRUITING`(모집 기간이 아님, #69) |
| `ApplicationConflictReason` | `APPLICATION_409` | `ALREADY_SUBMITTED`(이미 제출된 지원서를 다시 저장·제출하려 함, #69), `CONCURRENT_SAVE`(동시 요청으로 지원서가 막 생성됨, 다시 요청하면 됨, #69) |
| `ApplicationForbiddenReason` | `APPLICATION_403` | `SCHOOL_NOT_VERIFIED`(학교 인증을 하지 않은 회원, #69) |
| `ApplicationInputReason` | `COMMON_400` | `POSITION_FROM_ANOTHER_POSTING`, `QUESTION_FROM_ANOTHER_POSTING`, `QUESTION_NOT_FOR_POSITION`, `AVAILABLE_TIME_NOT_OPEN`(면접 가능 시간이 열려 있는 블록의 시작 시각이 아님), `ATTACHMENT_NOT_ALLOWED_FOR_QUESTION_TYPE`(FILE_LINK가 아닌 문항에 첨부를 보냄), `INVALID_ATTACHMENT_URL`(LINK가 http/https가 아니거나 FILE이 presigned 경로가 아님) (#69) |
| `SchoolInputReason` | `SCHOOL_400` | `CODE_MISMATCH`(인증번호가 다름), `CODE_EXPIRED`(5분이 지났거나 틀린 횟수가 많아 서버가 먼저 지움) (#76) |
| `SchoolConflictReason` | `SCHOOL_409` | `ALREADY_VERIFIED`(이미 학교 인증된 회원이 다시 인증 시도), `EMAIL_ALREADY_VERIFIED`(그 학교 메일이 이미 다른/같은 회원의 APPROVED 인증에 쓰임, 로그인 없이도 걸림) (#76) |

## fieldErrors를 응답에 담는 BusinessException

Bean Validation(`@Valid`, `ConstraintViolationException`)이 아닌 서비스 레벨 검증도 `extensions.fieldErrors`로
필드별 메시지를 보내야 할 때는 `BusinessException.withFieldErrors(List<FieldErrorResponse>)`를 쓴다 (#65에서
`FieldErrorResponse`를 api에서 common으로 옮기고 추가함). `addContext`와 달리 응답에 실제로 포함된다.
