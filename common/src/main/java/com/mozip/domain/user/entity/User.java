package com.mozip.domain.user.entity;

import java.time.LocalDateTime;

import com.mozip.global.common.BaseEntity;
import com.mozip.global.security.Authority;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	// 로그인 ID (숫자만, 예: 01012345678)
	@Column(nullable = false, unique = true, length = 20)
	private String phone;

	// BCrypt 해시
	@Column(nullable = false)
	private String password;

	@Column(nullable = false, length = 50)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Authority authority;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private UserStatus status;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Role lastRole;

	@Column(nullable = false)
	private boolean marketingAgreed;

	@Column(nullable = false)
	private LocalDateTime termsAgreedAt;

	private User(String phone, String encodedPassword, String name, Authority authority, Role lastRole,
		boolean marketingAgreed, LocalDateTime termsAgreedAt) {
		this.phone = phone;
		this.password = encodedPassword;
		this.name = name;
		this.authority = authority;
		this.status = UserStatus.ACTIVE;
		this.lastRole = lastRole;
		this.marketingAgreed = marketingAgreed;
		this.termsAgreedAt = termsAgreedAt;
	}

	// 일반 가입 (비밀번호는 암호화한 값으로 전달)
	public static User create(String phone, String encodedPassword, String name, boolean marketingAgreed,
		LocalDateTime termsAgreedAt) {
		return of(phone, encodedPassword, name, Authority.USER, Role.APPLICANT, marketingAgreed, termsAgreedAt);
	}

	// 권한과 역할을 지정해 생성 (관리자 등록, 개발용 회원 등 가입 흐름 밖에서 사용)
	public static User of(String phone, String encodedPassword, String name, Authority authority, Role lastRole,
		boolean marketingAgreed, LocalDateTime termsAgreedAt) {
		return new User(phone, encodedPassword, name, authority, lastRole, marketingAgreed, termsAgreedAt);
	}

	public boolean isActive() {
		return status == UserStatus.ACTIVE;
	}

}
