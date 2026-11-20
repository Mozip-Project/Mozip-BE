// Bash 실행 전에 git commit, gh pr create 명령에 AI 서명 줄이 있으면 차단 (docs/agent/rules/git.md)
// 입력은 stdin JSON (tool_input.command), 위반이면 exit 2와 stderr 이유
const fs = require('fs');

const input = JSON.parse(fs.readFileSync(0, 'utf8'));
const command = (input.tool_input && input.tool_input.command) || '';
if (!/git\s+commit|gh\s+pr\s+(create|edit)/.test(command)) {
	process.exit(0);
}

const signatures = [/Co-Authored-By/i, /Generated with/i];
const found = signatures.filter((pattern) => pattern.test(command));
if (found.length > 0) {
	process.stderr.write('[check-commit-message] 커밋 메시지와 PR 본문에 AI 서명 줄(Co-Authored-By, Generated with)을 넣지 않습니다. 그 줄을 빼고 다시 실행해 주세요\n규칙: docs/agent/rules/git.md\n');
	process.exit(2);
}
