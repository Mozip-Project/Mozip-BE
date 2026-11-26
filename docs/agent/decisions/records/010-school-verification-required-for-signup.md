# 회원가입에 학교 메일 인증을 필수로 포함한다

- 상태: proposed
- 기록일: 2026-11-26
- 관련: #76

## 맥락

이슈 #6(가입)은 학교 인증을 범위 밖(후속 이슈)으로 뒀고, #76(학교 인증)은 지금까지 가입 이후에만 호출할 수 있는 별도 기능으로 구현돼 있었다(`verifySchoolEmail`은 로그인 필요). 그래서 학교 인증 없이도 계정은 만들 수 있고, 지원서 제출 시점에만 `User.isSchoolVerified()`로 막는 구조였다. 이번에 "학교 인증을 해야 회원가입이 가능해야 한다"는 요구가 들어와, 가입 자체를 막는 지점을 다시 정해야 했다.

## 결정

- `verifySchoolEmail(email, code)`는 로그인 없이도 호출 가능(`@public`)하고 `Boolean!`을 반환한다. 코드 불일치/만료/모르는
  메일 도메인은 에러 없이 `false`만 응답한다. 성공하면 코드를 지우고 `school:verify:confirmed:{email}`을 10분간
  남긴다(`SchoolEmailVerificationCodeRepository.CONFIRMED_TTL`)
- 이미 confirmed 상태인 메일로 `verifySchoolEmail`을 다시 부르면 SCHOOL_409(reason: `EMAIL_ALREADY_CONFIRMED`)를 던진다.
  `requestSchoolEmailVerification`으로 새 코드를 받으면 이전 confirmed 상태는 같이 지워진다
- `POST /auth/signup`(`SignupRequest`)은 `schoolEmail`만 받는다(코드는 받지 않음). `AuthService.signup`은 휴대폰 번호
  중복 검사 다음, `User` 생성 직전에 `SchoolVerificationService.verifyForSignup(email)`로 confirmed 상태를 확인한다.
  없거나 10분이 지났으면 SCHOOL_400(reason: `EMAIL_NOT_VERIFIED`)
- 확인에 성공하면 `User`를 저장한 뒤 같은 트랜잭션에서 `SchoolVerificationService.completeSignupVerification(user, school, email)`을
  호출해 `SchoolVerification`(APPROVED) 레코드를 남기고 `user.verifySchool(school)`을 호출한다. 가입이 끝나면 계정은
  항상 학교 인증까지 끝난 상태다

## 근거

- `requestSchoolEmailVerification`이 이미 `@public`이라 로그인/가입 없이 인증번호를 받을 수 있어, `verifySchoolEmail`도
  같은 패턴으로 공개해 가입 폼을 "메일 확인 → 나머지 입력" 두 단계로 자연스럽게 나눌 수 있었다
- 가입 시점에 원래 코드를 다시 받아 비교하면, 코드를 아는 사람이면 누구나(가입 폼 전체를 입력한 시점까지 시간이 지나도)
  가입을 시도할 수 있다. confirmed 상태를 서버에서만 관리하고 TTL로 끊으면 "방금 그 메일을 확인한 사람만, 확인 후
  일정 시간 안에만" 가입할 수 있다는 걸 더 분명히 보장한다
- 코드 검증 로직(`findSchoolByEmailDomain`, 코드 비교, 틀린 횟수 기록, 삭제)은 `verifySchoolEmail`에 이미 있어
  `consumeValidCode`로 뽑아 재사용했고, 새 검증 로직을 만들지 않았다

## 고려한 대안

- 가입은 그대로 두고 로그인/가입 상태에 `PENDING_SCHOOL_VERIFICATION` 같은 `UserStatus`를 추가해 미인증 계정을 잠금: 로그인이 `user.isActive()`를 보고 막기 때문에, 미인증 상태에서 로그인 자체가 막히면 로그인 필요한 기존 `verifySchoolEmail`을 호출할 방법이 없어 막다른 구조가 됨
- (최초 기록 때 선택) 가입 시점에 원래 코드를 그대로 다시 검증: 구현은 더 단순하지만, 가입을 코드 자체가 아니라
  "방금 확인했다"는 서버 상태로 게이트하길 원해서(아래 변경 기록) confirmed 상태 방식으로 바꿈

## 영향 범위

- `common/src/main/java/com/mozip/domain/school/repository/SchoolEmailVerificationCodeRepository.java`:
  `isConfirmed`/`markConfirmed`/`deleteConfirmed` 추가, `saveCode`가 이전 confirmed 상태를 같이 지움
- `common/src/main/java/com/mozip/domain/school/exception/SchoolConflictReason.java`: `EMAIL_ALREADY_CONFIRMED` 추가
- `common/src/main/java/com/mozip/domain/school/exception/SchoolInputReason.java`: `EMAIL_NOT_VERIFIED` 추가
- `api/src/main/java/com/mozip/domain/auth/dto/SignupRequest.java`: `schoolEmail` 필드만 남음(`schoolVerificationCode` 제거)
- `api/src/main/java/com/mozip/domain/auth/service/AuthService.java`: `SchoolVerificationService` 의존 추가, `signup` 로직 변경
- `api/src/main/java/com/mozip/domain/school/service/SchoolVerificationService.java`: `verifySchoolEmail`이 confirmed 체크/표시를 하고,
  `verifyForSignup(email)`은 코드 대신 confirmed 상태만 확인(code 파라미터 제거)
- `api/src/main/java/com/mozip/domain/auth/controller/AuthControllerApi.java`: Swagger 설명과 에러 코드 목록 갱신
- 프론트: 가입 폼이 `requestSchoolEmailVerification` → `verifySchoolEmail`을 먼저 호출해 메일 확인을 마친 뒤,
  10분 안에 `schoolEmail`만 넣어 가입을 완료해야 함 (폼 순서 변경 필요, 코드는 가입 요청에 다시 보내지 않음)
- 테스트: `AuthIntegrationTest`, `AuthTokenStoreFailureTest`의 가입 헬퍼가 코드 대신 confirmed 상태를 미리 만들도록 변경됨

## 변경 기록

- 2026-11-26: 최초 기록. 가입 요청에 `schoolEmail`+`schoolVerificationCode`를 같이 받아 그 자리에서 코드를 검증하는 방식으로 기록
- 2026-11-27: `verifySchoolEmail`을 로그인 필요한 완료용(`Me` 반환)으로 남겨두는 대신, 가입 전 "코드 확인" 단계로 성격을 바꿔
  `requestSchoolEmailVerification`과 같은 `@public` 패턴으로 통일함. 별도로 만들었던 `checkSchoolEmailVerificationCode`
  뮤테이션은 `verifySchoolEmail`과 중복이라 합치고 삭제함
- 2026-11-27: 가입이 코드를 다시 검증하는 대신 `verifySchoolEmail`이 남긴 confirmed 상태(TTL 10분)만 확인하도록 바꿈.
  같은 메일로 confirmed 상태에서 `verifySchoolEmail`을 다시 부르면 SCHOOL_409(`EMAIL_ALREADY_CONFIRMED`)를 던지게 함.
  최초 기록에서 "고려한 대안"으로 적었던 티켓 방식을 이번에 실제로 채택함(가입을 "코드를 아는 것"이 아니라
  "서버가 방금 확인했다고 기억하는 것"으로 게이트하기 위함). `SignupRequest.schoolVerificationCode` 필드는 삭제됨
