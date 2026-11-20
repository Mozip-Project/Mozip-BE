# Git 규칙

## 브랜치

- GitHub 이슈를 만들면 `타입/#번호-이름` 브랜치가 `develop`에서 자동으로 생긴다 (예: `feat/#37-organization-create`). 그 브랜치에서 작업한다
- 이슈 없이 브랜치를 직접 만들지 않는다. 작업 단위가 생기면 이슈 템플릿(`.github/ISSUE_TEMPLATE/`)으로 이슈부터 만든다
- 앞 이슈 위에 쌓아 작업했으면 앞 PR이 머지된 뒤 `develop`으로 rebase한다

## 커밋

- 메시지는 `타입: 제목`. 제목은 50자 이내, 마침표 없이, 한국어로 (예: `feat: 단체 생성/조회 및 운영진 권한 검사`)
- 타입은 `.gitmessage`의 목록을 따른다. `feat`, `fix`, `refactor`, `chore`, `docs`, `test`, `style`, `rename`, `remove`
- 본문은 선택. 무엇을 왜 바꿨는지 적는다. 이슈 번호는 PR에서 `Closes #번호`로 연결하므로 커밋에는 보통 넣지 않는다
- `Co-Authored-By`, `Generated with ...` 같은 AI 서명 줄은 커밋과 PR 어디에도 넣지 않는다
- 커밋 전에 작업자에게 변경 요약을 보여 주고 확인을 받는다. 확인 없이 커밋하지 않는다
- 템플릿 적용: `git config commit.template .gitmessage`

## PR

- `develop`으로 올리고 squash 머지한다. PR 제목이 머지 커밋 메시지가 되므로 커밋과 같은 `타입: 제목` 형식으로 쓴다
- 본문은 `.github/PULL_REQUEST_TEMPLATE.md`를 채운다. 관련 이슈는 `Closes #번호`
- 엔티티를 바꿨으면 `db/ddl` SQL이 같은 PR에 있는지, 규칙을 바꿨으면 `docs/agent/rules/`를 고쳤는지 체크리스트에서 확인한다
- 리뷰어는 CODEOWNERS로 자동 등록된다
