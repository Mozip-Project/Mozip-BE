# 테스트 규칙

## 공통

- 테스트는 `common/src/test/java`에 두고 대상 클래스와 같은 패키지에 `*Test`로 만든다
- `@DisplayName`에 한국어 한 문장으로 기대 동작을 쓴다 (예: `"단체를 만들면 PENDING 상태로 저장되고 호출자가 대표자로 등록된다"`)
- 단언은 AssertJ. 예외는 `assertThatThrownBy(...).isInstanceOf(BusinessException.class)` 뒤에 `getErrorCode()`와 `getReason()`까지 확인한다
- Testcontainers로 PostgreSQL(pgvector)과 Redis를 띄운다. Docker Desktop이 꺼져 있으면 모든 통합 테스트가 실패한다
- 테스트 DB는 `db/ddl/*.sql`로 만들어지고 `ddl-auto: validate`로 엔티티와 대조한다. 엔티티만 바꾸고 SQL을 빠뜨리면 컨텍스트 로딩에서 실패한다 (`db.md`)

## 서비스 테스트

- `@Import(TestcontainersConfiguration.class)`, `@SpringBootTest`, `@Transactional`을 붙인다
- 테스트 메서드마다 트랜잭션을 열고 끝나면 롤백하므로 데이터가 남지 않는다. `@BeforeEach`에서 회원, 학교 같은 전제 데이터를 저장한다
- 영속성 컨텍스트 때문에 DB 제약을 확인해야 할 때는 `repository.flush()`를 부른다. unique 위반은 `DataIntegrityViolationException`으로 확인한다

## HTTP 테스트

- REST는 `@AutoConfigureMockMvc`, GraphQL은 `@AutoConfigureHttpGraphQlTester`로 `HttpGraphQlTester`를 쓴다
- HTTP 요청은 서버 스레드에서 따로 커밋되므로 `@Transactional`을 붙여도 롤백되지 않는다. 그래서 `@Transactional`을 붙이지 않는다
- 데이터가 남으므로 unique 컬럼(전화번호 등)에 고정값을 쓰지 않는다. 테스트마다 `ThreadLocalRandom`으로 지어서 충돌을 피한다
- 로그인 사용자는 `JwtProvider`로 토큰을 만들어 쿠키 헤더에 붙인다. 테스트 파일의 `asUser(user)` 헬퍼 참고
- 응답은 `.path("createOrganization.reviewStatus").entity(String.class).isEqualTo("PENDING")`처럼 필드마다 확인한다. 에러는 `errorType`과 `extensions.code`를 같이 본다

## 실행

```
./gradlew test                                                        # 전체
./gradlew :common:test --tests 'com.mozip.domain.organization.*'      # 패키지만
./gradlew :common:test --tests '*OrganizationServiceTest'             # 클래스만
```

- CI(`.github/workflows/ci.yml`)는 PR마다 `./gradlew build`를 돌리고 실패하면 테스트 리포트를 Artifacts로 올린다
