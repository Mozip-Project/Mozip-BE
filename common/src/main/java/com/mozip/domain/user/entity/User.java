package com.mozip.domain.user.entity;

import java.time.LocalDateTime;

import com.mozip.domain.organization.entity.Organization;
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

	// 운영진 홈에서 마지막으로 고른 단체 (GNB 단체 드롭다운), 아직 고른 적 없으면 null
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "last_organization_id")
	private Organization lastOrganization;

	// 학교 인증 승인 시 채움, null = 미인증 (공고 열람만 가능)
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "school_id")
	private School school;

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

	// 드롭다운에서 단체를 고르면 그 단체의 운영진 홈으로 가므로 lastRole도 OPERATOR로 함께 바꿈 (소속 검사는 서비스에서)
	public void selectOrganization(Organization organization) {
		this.lastOrganization = organization;
		this.lastRole = Role.OPERATOR;
	}

	// 내보내진 단체가 드롭다운의 현재 단체면 비움. lastRole은 두어 다른 소속 단체가 있으면 운영진 홈에 남음
	public void leaveOrganization(Organization organization) {
		if (lastOrganization != null && lastOrganization.getId().equals(organization.getId())) {
			this.lastOrganization = null;
		}
	}

}
