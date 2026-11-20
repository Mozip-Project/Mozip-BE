# 에러 규칙

## 에러 코드

- 도메인마다 `domain/{도메인}/exception/{도메인}ErrorCode` enum을 만들고 `ErrorCode` 인터페이스를 구현한다. 공통 코드는 `global/exception/CommonErrorCode`
- 코드 형식은 `도메인_HTTP상태`. 예: `ORG_404`, `COMMON_403`. 같은 도메인의 같은 HTTP 상태는 코드 하나만 둔다 (결정 003)
- 상수에는 `HttpStatus`, 코드, 사용자에게 보이는 한국어 메시지를 넣는다. 메시지는 마침표로 끝낸다

```java
@Getter
@RequiredArgsConstructor
public enum OrganizationErrorCode implements ErrorCode {

	// 이용권 없는 단체의 유료 기능 (공고 게시 등) 호출
	SUBSCRIPTION_REQUIRED(HttpStatus.PAYMENT_REQUIRED, "ORG_402", "단체 이용권이 없습니다."),
	ORGANIZATION_NOT_FOUND(HttpStatus.NOT_FOUND, "ORG_404", "단체를 찾을 수 없습니다.");

	private final HttpStatus status;
	private final String code;
	private final String message;

}
```

- `ErrorCodeRegistry`가 기동 시 `com.mozip` 아래 모든 `ErrorCode` enum을 스캔한다. 코드 문자열이 겹치면 앱이 뜨지 않는다. 새 코드를 넣기 전에 `docs/agent/references/error-codes.md`를 보고, 넣은 뒤에는 표를 갱신한다
- 초기에 만든 `AUTH_001`~`AUTH_007`, `USER_001`은 번호 접미사 형식이다. 바꾸지 않고 두되 새 코드는 `도메인_HTTP상태`로 만든다

## 같은 코드 안에서 상황 나누기

- 같은 코드로 응답하지만 프론트가 분기해야 하면 `reason` enum을 만든다. 번호 접미사(`MEMBER_409_1`)는 쓰지 않는다
- reason enum은 `domain/{도메인}/exception`에 두고 `BusinessException(errorCode, reason)`으로 던진다. 응답의 `extensions.reason`에 enum 이름이 들어간다
- `message`는 사람용, `reason`은 프론트 분기용이다

```java
// 단체 생성 입력의 필드 간 규칙 위반 (COMMON_400의 reason으로 응답해 프론트가 어느 필드인지 분기)
public enum OrganizationInputReason {
	COUNCIL_LEVEL_REQUIRED,
	SCHOOL_NOT_ALLOWED
}

throw new BusinessException(CommonErrorCode.INVALID_INPUT, OrganizationInputReason.COUNCIL_LEVEL_REQUIRED);
```

## 디버그 정보

- 로그에만 남길 값은 `addContext(key, value)`로 붙인다. 응답에는 들어가지 않는다

```java
throw new BusinessException(OrganizationErrorCode.ORGANIZATION_NOT_FOUND).addContext("organizationId", organizationId);
```

- 4xx는 warn, 5xx는 error 레벨로 리졸버가 찍는다. 서비스에서 따로 로그를 남기지 않는다

## 응답 형식

- 예외는 컨트롤러에서 잡지 않는다. `GlobalExceptionHandler`(REST)와 `GraphqlExceptionResolver`(GraphQL)가 아래 형식으로 바꾼다
- REST: `ApiResponse` 실패 응답. `code`, `message`, `traceId`, 검증 실패면 `data`에 필드별 에러 목록
- GraphQL: `errors[].extensions`에 `code`, `traceId`, 있으면 `reason`, 검증 실패면 `fieldErrors`. `errorType`은 400/401/403/404만 그대로 가고 그 외 4xx는 `BAD_REQUEST`, 5xx는 `INTERNAL_ERROR`라 세부 구분은 `code`로 한다
- `@Valid` 실패는 `COMMON_400`, `@PreAuthorize` 거부는 `COMMON_403`, 로그인 안 한 요청은 `COMMON_401` 또는 토큰 코드(`AUTH_002`, `AUTH_003`)로 자동 응답된다. 직접 던지지 않는다
- 자원이 없을 때는 도메인 404(`ORG_404`)를 쓴다. `COMMON_404`는 도메인을 특정할 수 없을 때만
