@AGENTS.md

## Claude Code 전용

- 스킬(`commit`, `pr`, `decision`): `.claude/skills/<이름>/SKILL.md`는 `.agents/skills/<이름>/SKILL.md`를 가리키는 연결 파일이다. 본문은 `.agents/skills/`에만 두고 양쪽을 따로 고치지 않는다
- hook: `.claude/hooks/`에 Java 주석 규칙과 커밋 서명 줄을 검사하는 스크립트가 있다. 등록은 각자 `.claude/settings.local.json`에 한다 (`.claude/hooks/README.md`). 막히면 우회하지 말고 `docs/agent/rules/code.md`, `git.md`에 맞게 고친다
