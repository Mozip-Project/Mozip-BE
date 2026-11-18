# 코드 규칙

## 주석

- `//` 한 줄 주석만 쓴다. `/** */` 자바독과 `/* */` 블록 주석은 쓰지 않는다. 클래스, 메서드 위에도 `//`로 쓴다
- 주석에는 핵심 동작과 그렇게 결정한 이유를 한 줄로 적는다. 코드를 다시 읊는 주석은 쓰지 않는다
- 주석 안에 중점(·)을 쓰지 않는다. 나열은 쉼표나 슬래시로 쓴다 (예: `401/403`)
- 이론 설명이나 배경은 주석에 넣지 않고 PR 본문이나 문서에 쓴다
- 다른 팀원 작업으로 넘기는 자리는 `// TODO(A 이용권): ...`처럼 누구 몫인지 괄호에 적는다

```java
// 조회된 항목의 organization은 프록시일 수 있기 때문에 id로 조회하여 결과 Map의 키는 GraphQL이 넘긴 부모 객체 그대로 사용
private static <T> Map<Organization, List<T>> groupByOrganization(...)
```

## 패키지

- 도메인 코드는 `com.mozip.domain.{도메인}` 아래 계층별 패키지로 둔다 (결정 001)

```
domain/{도메인}/
├── controller   REST 컨트롤러, Swagger 인터페이스(*ControllerApi), GraphQL 컨트롤러(*GraphqlController)
├── service      서비스, 다른 도메인이 쓰는 인터페이스
├── repository   Spring Data 리포지토리
├── entity       엔티티와 enum
├── dto          요청 입력, 응답 record
└── exception    {도메인}ErrorCode, reason enum
```

- 모든 도메인이 같이 쓰는 코드만 `com.mozip.global`에 둔다. 한 도메인에서만 쓰면 그 도메인 패키지에 둔다
- 개발 환경 전용 코드(인증 우회, 시드 데이터)는 `com.mozip.devtools`에 둔다
- 모듈은 `common`, `api`, `worker`. 지금은 거의 모든 코드가 `common`에 있다. "언젠가 worker도 쓸 것 같다"는 이유로 미리 `common`에 넣지 않고, 실제로 두 모듈이 필요로 할 때 내린다 (결정 006, 합의 전)

## 클래스 작성

- 들여쓰기는 탭. import 순서는 `java`, `org`와 `jakarta` 등 외부, `com.mozip`, 마지막에 `lombok` 순이고 그룹 사이는 빈 줄
- 생성자 주입은 `@RequiredArgsConstructor`와 `private final` 필드로 한다. `@Autowired` 필드 주입은 테스트에서만 쓴다
- Lombok은 `@Getter`, `@RequiredArgsConstructor`, `@NoArgsConstructor(access = PROTECTED)`, `@Slf4j` 정도만 쓴다. `@Setter`, `@Data`, `@Builder`는 쓰지 않는다
- 서비스는 클래스에 `@Transactional(readOnly = true)`, 쓰기 메서드에만 `@Transactional`을 붙인다
- 컨트롤러는 매핑과 서비스 호출만 한다. 분기와 검증은 서비스에 둔다
- 로그는 `@Slf4j`의 `log`로 남긴다. `System.out.println`은 쓰지 않는다

## DTO

- 요청 입력과 응답은 `record`로 만든다. GraphQL 입력은 `*Input`, REST 요청은 `*Request`, 응답은 `*Response`
- 입력 검증은 record 필드에 Bean Validation 어노테이션을 붙이고 컨트롤러 파라미터에 `@Valid`를 붙인다. 메시지는 사용자에게 그대로 보이므로 디자인 문구를 쓴다 (예: `@Size(max = 40, message = "최대 40자까지 입력할 수 있어요")`)
- 필드 간 규칙(학생회면 councilLevel 필수 등)은 어노테이션으로 표현하기 어려우니 서비스에서 검사하고 `reason`으로 어느 필드인지 알린다 (`error.md`)

## 엔티티

- `BaseEntity`를 상속한다 (`createdAt`, `updatedAt` 자동 기록)
- `@NoArgsConstructor(access = AccessLevel.PROTECTED)`와 private 생성자를 두고, 생성은 `static create(...)`로만 한다
- 상태 변경은 의미 있는 이름의 메서드로 한다 (`updateProfile`, `approve`). setter를 만들지 않는다
- enum 컬럼은 `@Enumerated(EnumType.STRING)`과 `length = 30`
- 연관관계는 `@ManyToOne(fetch = FetchType.LAZY)`. 컬렉션 매핑(`@OneToMany`)은 두지 않고 리포지토리에서 조회한다
- 컬럼 타입과 길이는 `db/ddl`의 SQL과 맞춘다. 어긋나면 테스트가 기동 시점에 실패한다 (`db.md`)

## 예외 구분

- 엔티티 불변식 위반은 `IllegalArgumentException`. 어떤 입력이 와도 그 상태가 될 수 없다는 뜻이다 (예: 연합 단체에 학교 지정)
- 서비스 규칙 위반은 `BusinessException(ErrorCode)`. 사용자 입력이나 권한 문제라 응답 코드로 알려야 하는 것이다 (`error.md`)
- 둘 다 컨트롤러에서 잡지 않는다. `GlobalExceptionHandler`와 `GraphqlExceptionResolver`가 응답으로 바꾼다
