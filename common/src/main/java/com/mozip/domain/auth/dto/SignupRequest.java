package com.mozip.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequest(

	@Schema(description = "휴대폰 번호 (숫자만)", example = "01012345678")
	@NotBlank
	@Pattern(regexp = "^01[016789]\\d{7,8}$", message = "휴대폰 번호 형식이 올바르지 않습니다.")
	String phone,

	@Schema(description = "비밀번호 (8~64자, 영문, 숫자 포함, 한글 등 ASCII 외 문자 불가)", example = "mozip1234")
	@NotBlank
	@Size(min = 8, max = 64, message = "비밀번호는 8자 이상 64자 이하여야 합니다.")
	// BCrypt는 72바이트까지만 처리하므로 1글자가 1바이트인 ASCII로 제한 (64자 = 64바이트)
	@Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)[\\x20-\\x7E]+$",
		message = "비밀번호는 영문과 숫자를 포함해야 하며 영문, 숫자, 특수문자만 사용할 수 있습니다.")
	String password,

	@Schema(description = "이름", example = "김모집")
	@NotBlank
	@Size(max = 50)
	String name,

	@Schema(description = "필수 약관 동의 (true여야 가입 가능)", example = "true")
	@AssertTrue(message = "필수 약관에 동의해야 합니다.")
	boolean termsAgreed,

	@Schema(description = "마케팅 수신 동의 (선택)", example = "false")
	boolean marketingAgreed

) {
}
