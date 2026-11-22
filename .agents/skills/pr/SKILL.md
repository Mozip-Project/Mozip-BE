---
name: pr
description: Mozip-BE PR 작성 절차. develop 대상, 템플릿 체크리스트와 Closes 연결, DDL과 규칙 문서 갱신 확인. 사용자가 PR 올려 달라고 할 때 쓴다.
---

# PR

규칙 본문은 `docs/agent/rules/git.md`. 여기서는 순서만 적는다.

## 올리기 전 확인

- `./gradlew build`가 통과했는지. 안 돌렸으면 돌린다
- 엔티티를 바꿨으면 같은 브랜치에 `db/ddl/이슈번호_설명.sql`이 있는지. 없으면 PR 전에 추가한다
- 에러 코드를 추가했으면 `docs/agent/references/error-codes.md`가 갱신됐는지
- 규칙을 바꿨으면 `docs/agent/rules/`가 갱신됐는지
- 브랜치가 이슈 자동 생성 브랜치(`타입/#번호-이름`)인지. 앞 PR 위에 쌓았으면 그 PR이 머지된 뒤 올린다

## 작성

- 대상은 `develop`. `gh pr create --base develop`
- 제목은 커밋과 같은 `타입: 제목` 형식. squash 머지 커밋 메시지가 된다
- 본문은 `.github/PULL_REQUEST_TEMPLATE.md` 구조를 그대로 채운다
    - 관련 이슈: `Closes #번호`. 여러 이슈면 줄마다 하나
    - 작업 내용: 바꾼 것을 묶어서 적는다. 임의로 정한 값이나 A와 확인할 것은 리뷰 요청 사항에
    - 체크리스트: 실제로 한 것만 체크한다. Swagger 확인을 안 했으면 비워 두고 이유를 적는다
- `Generated with`, `Co-Authored-By` 같은 서명 줄은 본문에 넣지 않는다
- 올린 뒤 URL을 작업자에게 알린다. 리뷰어는 CODEOWNERS로 자동 등록되므로 따로 지정하지 않는다
