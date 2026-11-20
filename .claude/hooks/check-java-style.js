// Edit/Write 전에 Java 파일 내용을 검사해 docs/agent/rules/code.md의 주석 규칙을 기계적으로 강제 (위반이면 exit 2로 차단)
// 입력은 stdin JSON (tool_input.file_path, content 또는 new_string), 이유는 stderr로 Claude에게 전달
const fs = require('fs');

const input = JSON.parse(fs.readFileSync(0, 'utf8'));
const filePath = (input.tool_input && input.tool_input.file_path) || '';
if (!filePath.endsWith('.java')) {
	process.exit(0);
}
const text = input.tool_input.content ?? input.tool_input.new_string ?? '';

const problems = [];
if (text.includes('/**')) {
	problems.push('자바독(/** */)은 쓰지 않습니다. // 한 줄 주석으로 바꿔 주세요');
}
if (/System\.out\.println/.test(text)) {
	problems.push('System.out.println 대신 @Slf4j의 log를 쓰세요');
}
// 주석 부분(// 뒤)에만 중점을 찾음. 문자열 리터럴 안의 중점은 허용
for (const line of text.split('\n')) {
	const index = line.indexOf('//');
	if (index >= 0 && line.slice(index).includes('·')) {
		problems.push('주석에 중점(·)을 쓰지 않습니다. 쉼표나 슬래시로 바꿔 주세요: ' + line.trim());
		break;
	}
}

if (problems.length > 0) {
	process.stderr.write('[check-java-style] ' + filePath + '\n- ' + problems.join('\n- ')
		+ '\n규칙: docs/agent/rules/code.md\n');
	process.exit(2);
}
