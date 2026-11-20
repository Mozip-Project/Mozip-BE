# Mozip-BE

대학 동아리, 학회, 학생회 모집 플랫폼 Mozip의 백엔드. Spring Boot 4.1, Java 21, PostgreSQL, Redis, GraphQL.

## 문서

- [`AGENTS.md`](AGENTS.md) 모듈 구조, 빌드와 테스트 명령, 규칙 읽기 순서. 사람과 AI 코딩 도구가 같이 읽는 진입점
- [`docs/agent/rules/`](docs/agent/rules/) 코드, 에러, GraphQL, DB, Git, 테스트 규칙
- [`docs/agent/references/`](docs/agent/references/) A/B 도메인 경계, 에러 코드 표
- [`docs/agent/decisions/`](docs/agent/decisions/) 구조와 규칙을 그렇게 정한 이유
- [`db/README.md`](db/README.md) DDL 파일 관리

## 실행

Docker Desktop을 켠 뒤 `./gradlew bootRun`. `compose.yaml`의 PostgreSQL과 Redis가 같이 뜬다. Swagger는 `http://localhost:8080/swagger-ui.html`.
