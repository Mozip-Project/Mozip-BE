# GraphQL 규칙

## 스키마 파일

- 프론트 API는 GraphQL로 제공하고 REST는 인증과 헬스 체크에만 쓴다 (결정 002)
- 스키마는 `common/src/main/resources/graphql/`에 도메인별 `.graphqls`로 나눈다 (`organization.graphqls`, `posting.graphqls`)
- 루트 `type Query`와 `type Mutation`은 `schema.graphqls`에만 둔다. 도메인 파일은 `extend type Query`, `extend type Mutation`으로 필드를 추가한다
- 공통 스칼라(`DateTime`, `Date`, `LocalTime`, `JSON`)와 디렉티브도 `schema.graphqls`에 있다
- 필드와 타입에는 큰따옴표 설명을 붙인다. 프론트가 GraphiQL과 codegen에서 보는 문서라 에러 코드와 조건을 적는다

```graphql
extend type Query {
    "단체 상세, 로그인 없이 조회 가능. 없으면 ORG_404"
    organization(id: ID!): Organization! @public
}
```

## 인증

- 모든 최상위 필드는 기본적으로 로그인이 필요하다. `GraphqlAuthInstrumentation`이 검사해서 없으면 `COMMON_401`로 응답한다
- 로그인 없이 열 필드에만 `@public`을 붙인다. 하위 필드에는 붙이지 않는다
- 컨트롤러에서 호출자는 `@AuthenticationPrincipal AuthUser authUser`로 받고 `authUser.userId()`를 쓴다
- 단체 소속 같은 도메인 권한은 Security가 아니라 서비스에서 검사한다 (`OrganizationAccessChecker`). 거부는 `BusinessException(CommonErrorCode.FORBIDDEN)`

## 컨트롤러

- `@Controller` 클래스에 `@QueryMapping`, `@MutationMapping`. 메서드 이름이 스키마 필드 이름이다
- 입력은 `@Argument @Valid` record로 받는다. 스키마의 `input` 타입과 record 필드 이름을 맞춘다
- 스키마에 있는 필드가 Java에 매핑이 없으면 기동 로그에 `Unmapped fields`로 나온다. 담당 이슈에서 채우기 전까지는 요청하면 에러가 나니 PR 본문에 적어 둔다

## N+1

- 부모 N개에 자식 목록이 붙는 필드(`Organization.members`, `Organization.keywords`)는 `@BatchMapping(typeName = "...")`으로 한 번에 조회한다
- 반환은 `Map<부모, List<자식>>`이고 키는 GraphQL이 넘긴 부모 객체 그대로 써야 한다. 자식의 부모 참조는 프록시일 수 있어 id로 묶는다 (`OrganizationGraphqlController.groupByOrganization` 참고)
- 리포지토리에는 `findAllByOrganizationInOrderById(Collection<Organization>)`처럼 부모 목록을 받는 메서드를 둔다
- 단건 조회에서 함께 쓰는 연관은 `join fetch` 쿼리 메서드로 가져온다 (`findWithSchoolById`)

## 에러

- GraphQL 에러 형식은 `error.md`. 테스트에서는 `errors().expect(e -> e.getErrorType() == ErrorType.FORBIDDEN && "COMMON_403".equals(e.getExtensions().get("code")))`처럼 `code`까지 확인한다
