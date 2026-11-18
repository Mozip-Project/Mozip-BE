# 모듈 분리 기준: 엔티티는 common, 로직은 api, 둘 다 쓸 때만 common으로 내린다

- 상태: proposed
- 기록일: 2026-11-18
- 관련: #20, #33

## 맥락

`common`, `api`, `worker` 세 모듈이 있지만 지금은 컨트롤러, Security, GraphQL까지 전부 `common`에 있다. `api`는 진입점과 yml, `worker`는 Redis 리스너만이다. worker는 아직 common을 의존하지 않아서 문제가 드러나지 않았지만, 의존하는 순간 web과 Security 자동 설정이 worker에도 붙는다. 팀원 A가 분리 기준을 제안했다.

## 결정

- `common`: 엔티티, 리포지토리, enum, `ErrorCode`와 `BusinessException`, 도메인별 `*ErrorCode`, `BaseEntity`, JPA와 Redis 설정
- `api`: 컨트롤러, 요청과 응답 DTO, Security와 JWT 필터, `GlobalExceptionHandler`와 `GraphqlExceptionResolver`, API 유스케이스 서비스
- `worker`: Redis 리스너, 비동기 작업 서비스. 도메인 서비스를 호출하지 않고 리포지토리와 엔티티 메서드만 쓴다
- 비즈니스 로직은 기본 `api`. 두 모듈이 실제로 같이 쓸 때만 `common`으로 내린다. "언젠가 worker도 쓸 것 같다"는 이유로 미리 내리지 않는다
- 엔티티 하나의 상태 규칙은 엔티티 메서드로 둔다 (`application.submit()`). 양쪽이 같은 규칙을 쓴다

## 근거

- worker가 Tomcat과 Security 없이 가볍게 뜬다
- "둘 다 쓸 때만 내린다"가 common 비대화를 막는다. 지금처럼 common이 앱 전체인 상태가 가장 나쁘다
- worker가 서비스 대신 리포지토리와 엔티티 메서드만 쓰면 서비스를 common으로 내릴 일이 거의 없어 연쇄 이동이 생기지 않는다

## 고려한 대안

- 모듈 넷(`domain`, `infra`, `api`, `worker`): 2인 MVP에서 모듈 경계를 넘을 때마다 Gradle을 건드리는 비용이 더 크다
- worker 제거, `@Async`로 api 안에서 처리: 보장 수준은 같지만 worker 모듈과 k8s 배포가 이미 있어 되돌리는 비용이 있다

## 영향 범위

- 이동할 때 같이 해야 하는 것: `WorkerApplication`을 `com.mozip` 패키지로 (아니면 엔티티 스캔이 안 됨), common에 `java-library`와 `api(...)` 의존성 노출, 통합 테스트를 `api/src/test`로, worker yml에 datasource와 k8s secret
- `docs/agent/rules/code.md` 패키지 절, 결정 001의 패키지 트리
- 지금이 가장 싼 시점이다. 컨트롤러가 `auth`와 `organization` 둘뿐이다

## 변경 기록

- 2026-11-18: A 제안과 검토 결과를 기록. 이동 작업은 별도 refactor 이슈로
