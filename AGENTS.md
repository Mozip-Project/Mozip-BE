# Mozip-BE

대학 동아리, 학회, 학생회 모집 플랫폼 Mozip의 백엔드. Spring Boot 4.1, Java 21, PostgreSQL(pgvector), Redis. 프론트와는 GraphQL로, 인증과 헬스 체크는 REST로 통신한다.

이 파일은 AI 코딩 도구(Codex, Claude Code 등)와 사람이 같이 읽는 진입점이다. 규칙 본문은 `docs/agent/rules/`에 있고 여기서는 읽기 순서만 안내한다.

## 모듈

| 모듈 | 넣는 것 | 비고 |
|---|---|---|
| `common` | 엔티티, 리포지토리, 서비스, 컨트롤러, GraphQL 스키마, 공통 설정과 예외 | 지금은 거의 모든 코드가 여기 있다 |
| `api` | 실행 진입점 `MozipApplication`, 환경별 `application-*.yml` | 운영 배포 단위 |
| `worker` | Redis 리스너, 비동기 작업 | `common`에 의존하지 않는다 |

도메인 코드는 `common/src/main/java/com/mozip/domain/{도메인}/` 아래 계층별 패키지로 둔다. 자세한 구조는 `docs/agent/rules/code.md`.

## 빌드와 테스트

```
./gradlew build                                              # CI와 같은 전체 빌드와 테스트
./gradlew test                                               # 테스트만
./gradlew :common:test --tests 'com.mozip.domain.organization.*'   # 일부만
./gradlew bootRun                                            # 로컬 실행 (compose.yaml의 DB, Redis 자동 기동)
```

- 테스트는 Testcontainers로 PostgreSQL과 Redis 컨테이너를 띄우므로 Docker Desktop이 켜져 있어야 한다
- 테스트 DB는 `db/ddl/*.sql`로 만들고 `ddl-auto: validate`로 엔티티와 대조한다. 엔티티만 바꾸고 SQL을 빠뜨리면 컨텍스트 로딩에서 실패한다
- 로컬 실행 후 Swagger는 `http://localhost:8080/swagger-ui.html`, GraphiQL은 `http://localhost:8080/graphiql`

## 규칙 읽기 순서

코드를 쓰기 전에 `docs/agent/rules/`를 아래 순서로 읽는다. 규칙과 코드가 다르면 규칙을 따르고, 규칙 자체를 바꿔야 하면 PR에서 문서도 같이 고친다.

- `code.md` 주석, 패키지, DTO, 엔티티, 예외 구분
- `error.md` 에러 코드 형식과 `reason`, `addContext`
- `graphql.md` 스키마 파일 배치, `@public`, `@BatchMapping`
- `db.md` DDL 파일 규칙 (본문은 `db/README.md`)
- `git.md` 브랜치, 커밋, PR
- `test.md` 서비스 테스트와 HTTP 테스트 차이

사실 자료는 `docs/agent/references/`에 있다.

- `ab-boundary.md` 팀원 A와 B의 도메인 경계, 서로 호출하는 인터페이스
- `error-codes.md` 현재 에러 코드 표. 코드를 추가하면 같이 갱신한다

규칙이 왜 그렇게 정해졌는지는 `docs/agent/decisions/records/`에 있다. 규칙 문장 끝의 `(결정 003)`이 그 번호다. 구조나 규칙을 새로 정했으면 `.agents/skills/decision` 절차로 기록한다.

아직 해소되지 않은 문제는 폴더에 기록하지 않고 GitHub 이슈에 `🙋 question` 또는 `⏸️ on hold` 라벨로 둔다. `gh issue list --label "⏸️ on hold"`로 본다.

## 작업 흐름

- GitHub 이슈를 만들면 `타입/#번호-이름` 브랜치가 `develop`에서 자동으로 생긴다. 그 브랜치에서 작업한다
- PR은 `develop`으로 올리고 squash 머지한다. 본문은 `.github/PULL_REQUEST_TEMPLATE.md` 체크리스트를 채운다
- 커밋, PR, 결정 기록은 `.agents/skills/commit`, `pr`, `decision`의 절차를 따른다

## 건드리지 않는 것

- `api/src/main/resources/application-*.yml`의 비밀값과 `*.pem`, `*.key` 파일은 읽거나 출력하지 않는다
- `local/`은 개인 작업 메모라 gitignore에 있다. 규칙의 근거로 삼지 않는다
- 이미 `develop`에 들어간 `db/ddl/*.sql`은 수정하지 않는다
