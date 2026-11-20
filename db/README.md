# db

운영 DB 스키마는 이 폴더의 SQL 파일로 관리한다. 엔티티만 바꾸고 SQL을 빠뜨리면 테스트가 컨텍스트 로딩에서 실패한다.

## 파일 이름

`db/ddl/NNN_#이슈번호_설명.sql`

- `NNN`은 세 자리 순번. 적용 순서를 정한다
- `#이슈번호`는 그 변경이 나온 GitHub 이슈. 누가 왜 넣었는지 파일 목록만 봐도 알 수 있게 한다
- `설명`은 영어 소문자와 밑줄로 짧게

예시

```
001_#36_baseline.sql              develop 7dee48d 시점 전체 스키마 (#36에서 추가)
002_#37_organization_create.sql   단체 생성에 필요한 컬럼과 인덱스
```

파일 첫 줄에는 파일 이름, 둘째 줄부터 무엇을 왜 바꾸는지 한두 줄 적는다.

## 규칙

- 한 PR에서 엔티티를 바꿨으면 같은 PR에 SQL 파일을 하나 추가한다. 여러 변경이어도 PR 하나면 파일 하나
- 이미 develop에 들어간 파일은 고치지 않는다. 되돌리거나 더 바꿀 때도 새 번호로 파일을 추가한다
- 번호가 겹치면 나중에 merge되는 쪽이 rebase하면서 번호를 올린다. 운영 DB에는 아직 안 올라간 상태여야 한다
- JPA로 표현할 수 없는 제약(부분 unique index 등)도 여기에 넣는다. 그런 경우 SQL 파일 주석에 "JPA로 표현할 수 없어 DDL에만" 같은 식으로 적어 둔다
- 컬럼 타입과 길이는 엔티티 어노테이션과 맞춘다. 테스트의 `ddl-auto: validate`는 테이블과 컬럼이 있는지, 타입이 맞는지만 본다. varchar 길이, nullable, check 제약은 비교하지 않으므로 리뷰에서 직접 확인한다
- 이미 있을 수 있는 것을 만드는 문장은 `add column if not exists`, `create index if not exists`로 쓴다. dev/prod는 `ddl-auto: update`라 배포가 먼저 되면 Hibernate가 컬럼을 먼저 만들어 둔다. 부분 unique index처럼 JPA로 표현할 수 없는 것은 Hibernate가 만들지 않으니 SQL을 꼭 실행한다

## 사용처

- 테스트: `TestcontainersConfiguration`이 이 폴더를 PostgreSQL 컨테이너의 `/docker-entrypoint-initdb.d/`에 복사한다. 컨테이너가 처음 뜰 때 파일 이름 순으로 실행되고, 그 위에서 `ddl-auto: validate`가 엔티티와 테이블을 대조한다
- 운영과 개발 서버: 자동 적용하지 않는다. 배포 전에 아직 적용 안 한 파일을 번호 순으로 직접 실행한다. 어디까지 적용했는지는 배포 체크리스트에 적는다

## 이미 돌고 있는 DB에는 어떻게 적용하나?

- `001_#36_baseline.sql`은 실행하지 않음
  - develop 7dee48d 시점 스키마를 그대로 옮긴 파일이라 기존 DB에는 이미 같은 테이블이 있음
  - `create table`이라 실행하면 첫 문장에서 실패함
- `002`부터 번호 순으로 실행함
- 적용 전에 `\d members`처럼 대상 테이블을 보고, 파일이 가정한 제약 이름과 실제 이름이 같은지 확인함
  - Hibernate가 만든 제약은 이름이 `UK...` 해시라 SQL의 `drop constraint 이름`이 맞지 않을 수 있음
- 새 DB(테스트 컨테이너, 새 환경)는 001부터 전부 실행함
