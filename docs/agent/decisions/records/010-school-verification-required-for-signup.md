# 회원가입에 학교 메일 인증을 필수로 포함한다

- 상태: proposed
- 기록일: 2026-11-26
- 관련: #76

## 맥락

이슈 #6(가입)은 학교 인증을 범위 밖(후속 이슈)으로 뒀고, #76(학교 인증)은 지금까지 가입 이후에만 호출할 수 있는 별도 기능으로 구현돼 있었다(`verifySchoolEmail`은 로그인 필요). 그래서 학교 인증 없이도 계정은 만들 수 있고, 지원서 제출 시점에만 `User.isSchoolVerified()`로 막는 구조였다. 이번에 "학교 인증을 해야 회원가입이 가능해야 한다"는 요구가 들어와, 가입 자체를 막는 지점을 다시 정해야 했다.

## 결정

- `POST /auth/signup`(`SignupRequest`)에 `schoolEmail`, `schoolVerificationCode`를 필수 필드로 추가한다
- `AuthService.signup`은 휴대폰 번호 중복 검사 다음, `User` 생성 직전에 `SchoolVerificationService.verifyForSignup(email, code)`로 인증번호를 검증한다(기존 `requestSchoolEmailVerification`으로 미리 받은 코드)
- 검증에 성공하면 `User`를 저장한 뒤 같은 트랜잭션에서 `SchoolVerificationService.completeSignupVerification(user, school, email)`을 호출해 `SchoolVerification`(APPROVED) 레코드를 남기고 `user.verifySchool(school)`을 호출한다. 가입이 끝나면 계정은 항상 학교 인증까지 끝난 상태다
- 로그인 후 쓰는 기존 `verifySchoolEmail`(재인증/보완용)은 그대로 둔다. 이 변경 전에 만들어진(학교 인증 없는) 계정을 나중에 인증시키는 경로로 남겨둠

## 근거

- `requestSchoolEmailVerification`이 이미 `@public`이라 로그인/가입 없이 인증번호를 받을 수 있어, 가입 요청에 이메일+코드를 같이 보내는 것만으로 추가 엔드포인트 없이 끝낼 수 있었다
- 코드 검증 로직(`findSchoolByEmailDomain`, 코드 비교, 틀린 횟수 기록, 삭제)은 `verifySchoolEmail`에 이미 있어 `consumeValidCode`로 뽑아 재사용했고, 새 검증 로직을 만들지 않았다
- 인증번호 검증을 휴대폰 중복 검사보다 뒤에 둔 건, 번호가 중복이면 코드를 소비하지 않아 같은 코드로 다시 시도할 수 있게 하기 위함

## 고려한 대안

- 가입은 그대로 두고 로그인/가입 상태에 `PENDING_SCHOOL_VERIFICATION` 같은 `UserStatus`를 추가해 미인증 계정을 잠금: 로그인이 `user.isActive()`를 보고 막기 때문에, 미인증 상태에서 로그인 자체가 막히면 로그인 필요한 `verifySchoolEmail`을 호출할 방법이 없어 막다른 구조가 됨
- `requestSchoolEmailVerification` → 별도의 "가입 전 인증 확정" 뮤테이션 → `signup` 3단계로 분리: Redis에 "이 메일은 가입용으로 확정됐다"는 티켓 상태를 새로 만들어야 해서 상태가 하나 더 늘고, 가입 시점에 코드를 직접 검증하는 것보다 구현이 커짐. 지금 방식대로도 요청(1단계)과 가입(2단계)만으로 끝나 선택하지 않음

## 영향 범위

- `api/src/main/java/com/mozip/domain/auth/dto/SignupRequest.java`: `schoolEmail`, `schoolVerificationCode` 필드 추가
- `api/src/main/java/com/mozip/domain/auth/service/AuthService.java`: `SchoolVerificationService` 의존 추가, `signup` 로직 변경
- `api/src/main/java/com/mozip/domain/school/service/SchoolVerificationService.java`: `verifyForSignup`, `completeSignupVerification` 추가, `verifySchoolEmail`은 `consumeValidCode`로 중복 제거
- `api/src/main/java/com/mozip/domain/auth/controller/AuthControllerApi.java`: Swagger 설명과 에러 코드 목록 갱신
- 프론트: 가입 폼이 `requestSchoolEmailVerification`을 먼저 호출해 인증번호를 받고, 가입 요청에 `schoolEmail`/`schoolVerificationCode`를 같이 보내야 함 (폼 순서 변경 필요)
- 테스트: `AuthIntegrationTest`, `AuthTokenStoreFailureTest`, `AuthServiceTest`의 가입 헬퍼가 학교 메일/코드를 같이 보내도록 변경됨

## 변경 기록

- 2026-11-26: 최초 기록
