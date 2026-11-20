# DDL은 db/ddl SQL 파일로 수동 관리하고 Flyway는 쓰지 않는다

- 상태: proposed
- 기록일: 2026-11-17
- 관련: #36, PR #40

## 맥락

운영은 `ddl-auto: none`이고 마이그레이션 도구가 없었다. 초기 스택 문서에는 Flyway가 있었지만 넣지 않은 상태였다. B는 거의 매 PR에 컬럼과 테이블이 생겨 두 사람이 같은 DB 구조를 유지할 방법이 필요했다.

## 결정

- 리포에 `db/ddl/NNN_#이슈번호_설명.sql`을 두고 엔티티를 바꾼 PR마다 파일 하나를 추가한다. PostgreSQL 문법
- 운영과 개발 서버에는 배포 전에 번호 순으로 직접 실행한다
- 테스트는 Testcontainers가 이 폴더를 initdb로 실행하고 `ddl-auto: validate`로 엔티티와 대조한다
- Flyway는 도입하지 않는다

## 근거

- SQL을 빠뜨리면 테스트가 컨텍스트 로딩에서 먼저 실패해서 리뷰 전에 드러난다. Flyway 없이도 "엔티티와 DDL이 어긋나면 터진다"는 효과를 얻는다
- 2인 팀에서 Flyway의 체크섬 관리, 실패한 마이그레이션 복구, 로컬 DB 초기화 비용이 득보다 크다고 봤다
- 파일 이름에 이슈 번호가 있어 누가 왜 넣었는지 목록만 봐도 보인다

## 고려한 대안

- Flyway: 자동 적용은 편하지만 머지 순서가 바뀌면 버전 충돌을 매번 풀어야 하고, 운영 자동 적용은 아직 원하지 않는다
- `ddl-auto: update` 유지: 컬럼 삭제와 타입 변경이 반영되지 않고 운영 DB가 의도치 않게 바뀐다

## 영향 범위

- `db/README.md`, `docs/agent/rules/db.md`
- `TestcontainersConfiguration`이 `db/ddl`을 컨테이너에 복사. 테스트 `ddl-auto: validate`
- 운영 `ddl-auto`를 `validate`로 바꿀지는 미정. 바꾸면 배포 전 DDL 적용을 빠뜨렸을 때 기동 시점에 실패한다

## 변경 기록

- 2026-11-17: 제안. `001_#36_baseline.sql`과 `db/README.md`를 PR #40에 포함
