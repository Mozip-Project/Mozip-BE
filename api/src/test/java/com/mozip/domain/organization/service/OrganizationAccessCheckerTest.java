package com.mozip.domain.organization.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import com.mozip.TestcontainersConfiguration;
import com.mozip.devtools.seed.SeedFixtures;
import com.mozip.domain.organization.entity.Member;
import com.mozip.domain.organization.entity.MemberRole;
import com.mozip.domain.organization.entity.Organization;
import com.mozip.domain.organization.exception.OrganizationErrorCode;
import com.mozip.domain.organization.repository.MemberRepository;
import com.mozip.domain.user.entity.User;
import com.mozip.domain.user.repository.UserRepository;
import com.mozip.global.exception.BusinessException;

// 테스트 메서드마다 트랜잭션을 열고 끝나면 롤백해 다른 테스트에 데이터가 남지 않음
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class OrganizationAccessCheckerTest {

	@Autowired
	private OrganizationAccessChecker accessChecker;

	@Autowired
	private OrganizationService organizationService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private MemberRepository memberRepository;

	private User representative;
	private User operator;
	private User outsider;
	private Organization organization;

	@BeforeEach
	void setUp() {
		representative = userRepository.save(User.create("01099990001", "encoded", "대표자", false, LocalDateTime.now()));
		operator = userRepository.save(User.create("01099990003", "encoded", "운영진", false, LocalDateTime.now()));
		outsider = userRepository.save(User.create("01099990002", "encoded", "외부인", false, LocalDateTime.now()));
		organization = organizationService.createOrganization(representative.getId(),
			SeedFixtures.createOrganizationInput());
		memberRepository.save(Member.create(organization, operator, null, MemberRole.OPERATOR));
	}

	@Test
	@DisplayName("대표자든 운영진이든 단체 소속이면 isOperatorOf가 true다")
	void isOperatorOfMember() {
		assertThat(accessChecker.isOperatorOf(representative.getId(), organization.getId())).isTrue();
		assertThat(accessChecker.isOperatorOf(operator.getId(), organization.getId())).isTrue();
	}

	@Test
	@DisplayName("단체에 소속되지 않은 회원이면 isOperatorOf가 false다")
	void isOperatorOfOutsider() {
		assertThat(accessChecker.isOperatorOf(outsider.getId(), organization.getId())).isFalse();
	}

	@Test
	@DisplayName("내보내진 운영진은 isOperatorOf와 isRepresentativeOf 모두 false다")
	void leftMember() {
		memberRepository.findAllByUserIdAndLeftAtIsNull(operator.getId()).getFirst().leave(LocalDateTime.now());

		assertThat(accessChecker.isOperatorOf(operator.getId(), organization.getId())).isFalse();
		assertThat(accessChecker.isRepresentativeOf(operator.getId(), organization.getId())).isFalse();
	}

	@Test
	@DisplayName("isRepresentativeOf는 대표자만 true다")
	void isRepresentativeOf() {
		assertThat(accessChecker.isRepresentativeOf(representative.getId(), organization.getId())).isTrue();
		assertThat(accessChecker.isRepresentativeOf(operator.getId(), organization.getId())).isFalse();
		assertThat(accessChecker.isRepresentativeOf(outsider.getId(), organization.getId())).isFalse();
	}

	@Test
	@DisplayName("getOrganization은 저장된 단체를 돌려준다")
	void getOrganization() {
		Organization found = accessChecker.getOrganization(organization.getId());

		assertThat(found.getId()).isEqualTo(organization.getId());
		assertThat(found.getName()).isEqualTo(SeedFixtures.ORGANIZATION_NAME);
	}

	@Test
	@DisplayName("없는 단체를 조회하면 ORG_404 BusinessException이다")
	void getOrganizationNotFound() {
		assertThatThrownBy(() -> accessChecker.getOrganization(Long.MAX_VALUE))
			.isInstanceOfSatisfying(BusinessException.class, e -> {
				assertThat(e.getErrorCode()).isEqualTo(OrganizationErrorCode.ORGANIZATION_NOT_FOUND);
				assertThat(e.getContext()).containsEntry("organizationId", Long.MAX_VALUE);
			});
	}

}
