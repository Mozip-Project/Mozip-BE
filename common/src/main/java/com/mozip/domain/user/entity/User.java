package com.mozip.domain.user.entity;

import java.time.LocalDateTime;

import com.mozip.domain.school.entity.School;
import com.mozip.global.common.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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

	// 학교 인증 승인 시 채움, null = 미인증 (공고 열람만 가능)
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "school_id")
	private School school;

	@Column(nullable = false)
	private boolean marketingAgreed;

	@Column(nullable = false)
	private LocalDateTime termsAgreedAt;

	// 아래 프로필 필드는 전부 선택 입력이라 가입 시점엔 비어 있고, 프로필 수정 기능에서 채워짐
	@Column(length = 500)
	private String profileImageUrl;

	@Column(length = 100)
	private String department;

	private Integer grade;

	@Column(length = 100)
	private String contactEmail;

	// 지원 결과, 면접 일정 알림
	@Column(nullable = false)
	private boolean notifyResult;

	// 스크랩 공고 지원 시작 알림
	@Column(nullable = false)
	private boolean notifyScrap;

	// 대표자 인수 요청, 평가 마감 알림
	@Column(nullable = false)
	private boolean notifyOperator;

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
		this.notifyResult = true;
		this.notifyScrap = true;
		this.notifyOperator = true;
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

	public void updateNotificationSettings(boolean notifyResult, boolean notifyScrap, boolean notifyOperator) {
		this.notifyResult = notifyResult;
		this.notifyScrap = notifyScrap;
		this.notifyOperator = notifyOperator;
	}

	// 지원자/운영진 홈 전환
	public void updateLastRole(Role lastRole) {
		this.lastRole = lastRole;
	}

	// TODO(학교 인증 이슈): 실제 인증 흐름이 생기기 전까지 테스트·시드에서 학교 인증 상태를 만들 때 씀
	public void verifySchool(School school) {
		this.school = school;
	}

}
