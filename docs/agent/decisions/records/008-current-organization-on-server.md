# 운영진의 현재 단체는 서버에 저장하고 GraphQL로 고른다

- 상태: accepted
- 기록일: 2026-11-20
- 관련: #52, #53. 결정 007을 대체

## 맥락

결정 007은 현재 단체를 쿠키로 보내고 선택과 전환은 REST로 하기로 했다. #52에서 운영진 홈 드롭다운과 로그인 후 이동을 만들면서 쿠키를 누가 굽고 어디에 쓰는지 다시 정해야 했다. 프론트는 미들웨어에서 현재 단체를 읽을 수 있기를 원했다.

## 결정

- 마지막으로 고른 단체는 `User.lastOrganization`에 저장한다. 드롭다운에서 고르면 `Mutation.selectOrganization`이 이 값을 바꾸고 `lastRole`을 `OPERATOR`로 바꾼다
- 프론트는 `Query.me`의 `lastRole`, `lastOrganization`, `memberships`로 드롭다운과 첫 화면을 그린다
- 운영진용 필드는 대상 단체를 `organizationId` 인자로 받고 요청마다 소속을 검사한다. 서버는 현재 단체를 기억해 두고 쓰지 않는다
- 백엔드는 현재 단체 쿠키를 굽지 않는다. 미들웨어에 필요하면 프론트가 `me`나 `selectOrganization` 응답으로 자기 도메인에 굽고, 어느 화면을 띄울지 정하는 데만 쓴다

## 근거

- `selectOrganization`은 GraphQL이라 응답에 Set-Cookie를 붙이려면 인터셉터를 따로 만들어야 한다. REST로 빼면 결정 002의 REST 범위가 다시 넓어진다
- 백엔드 쿠키는 `api.mo-zip.com`에 HttpOnly로 붙는다. 프론트 미들웨어가 읽으려면 domain을 넓히고 HttpOnly를 빼야 한다
- 서버 값이 바뀌어도 브라우저에 남은 쿠키는 서버가 고칠 수 없다. 운영진을 내보내 `lastOrganization`이 비어도 그 사람 쿠키에는 옛 단체가 남는다
- 쿠키는 탭끼리 공유된다. 탭 두 개에서 다른 단체를 열면 한쪽에서 바꾼 값이 다른 탭 요청에도 실려 권한 판단에 쓸 수 없다
- 서버에 저장하면 기기가 바뀌어도 마지막으로 보던 단체로 들어간다

## 고려한 대안

- 결정 007 그대로 (REST로 서버가 쿠키를 심고 운영진 필드가 쿠키에서 단체를 읽음): 탭 공유와 남는 쿠키 때문에 요청이 엉뚱한 단체에 적용될 수 있다
- 프론트 쿠키로만 관리하고 `lastOrganization`, `selectOrganization`을 뺌: 전환할 때 요청이 없어 단순하지만 새 기기나 쿠키가 지워진 브라우저에서는 마지막 단체를 기억하지 못한다

## 영향 범위

- `docs/agent/rules/graphql.md` 인증 항목
- 결정 002의 REST 범위는 인증과 헬스 체크로 되돌린다
- `User.lastOrganization` 컬럼(`db/ddl/004_#52_user_last_organization.sql`), `UserGraphqlController.me`, `OrganizationGraphqlController.selectOrganization`
- A의 운영진용 필드(공고, 지원서, 평가)도 `organizationId`를 인자로 받고 `OrganizationAccessChecker`로 소속을 검사한다

## 변경 기록

- 2026-11-20: 프론트와 #53 방식으로 합의해 기록. 결정 007을 대체
