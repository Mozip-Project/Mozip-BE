# Claude Code hook

Claude Code가 도구를 실행하기 직전에 돌리는 검사 스크립트. 리포에는 스크립트만 두고 등록은 각자 `.claude/settings.local.json`(gitignore)에 한다. 쓰고 싶은 사람만 켠다.

- `check-java-style.js` Java 파일을 Edit/Write할 때 `/**` 자바독, 주석 안 중점, `System.out.println`이 있으면 막는다. 규칙은 `docs/agent/rules/code.md`
- `check-commit-message.js` `git commit`, `gh pr create` 명령에 AI 공동저자 서명 줄이 있으면 막는다. 규칙은 `docs/agent/rules/git.md`

등록 예시 (`.claude/settings.local.json`)

```json
{
  "hooks": {
    "PreToolUse": [
      {
        "matcher": "Edit|Write",
        "hooks": [{ "type": "command", "command": "node \"$CLAUDE_PROJECT_DIR/.claude/hooks/check-java-style.js\"", "timeout": 5 }]
      },
      {
        "matcher": "Bash",
        "hooks": [{ "type": "command", "command": "node \"$CLAUDE_PROJECT_DIR/.claude/hooks/check-commit-message.js\"", "timeout": 5 }]
      }
    ]
  }
}
```

스크립트는 stdin으로 도구 입력 JSON을 받고, 위반이 있으면 exit 2로 끝내 그 호출을 취소한다. stderr 메시지는 Claude에게 전달된다.
