package com.mozip.devtools.seed;

import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.mozip.devtools.authbypass.MockRole;
import com.mozip.devtools.authbypass.MockUserRegistry;
import com.mozip.domain.organization.entity.Member;
import com.mozip.domain.organization.entity.Organization;
import com.mozip.domain.organization.repository.MemberRepository;
import com.mozip.domain.organization.repository.OrganizationRepository;
import com.mozip.domain.user.entity.User;
import com.mozip.domain.user.repository.UserRepository;
import com.mozip.global.security.ConditionalOnAuthBypass;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// 인증 우회 중 앱 시작 시 개발용 OPERATOR를 개발용 단체의 대표자로 등록 (이미 소속 단체가 있으면 재사용)
// TODO(A 이용권): 이용권 모델이 들어오면 개발용 단체에 무제한 이용권을 같이 넣어야 ORG_402에 막히지 않음
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnAuthBypass
@Order(SeedRunner.ORDER)
public class SeedRunner implements ApplicationRunner {

	// 개발용 회원을 먼저 생성하기 위해 MockUserRegistry 다음 순서로 실행
	public static final int ORDER = MockUserRegistry.ORDER + 1;

	private final MockUserRegistry mockUserRegistry;
	private final UserRepository userRepository;
	private final OrganizationRepository organizationRepository;
	private final MemberRepository memberRepository;

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		Long operatorId = mockUserRegistry.get(MockRole.OPERATOR).userId();
		List<Member> memberships = memberRepository.findAllByUserId(operatorId);
		Member member = memberships.isEmpty() ? createOrganizationWithRepresentative(operatorId) : memberships.getFirst();
		log.warn("개발용 단체 준비: organizationId={}, representativeUserId={}", member.getOrganization().getId(),
			operatorId);
	}

	private Member createOrganizationWithRepresentative(Long operatorId) {
		User operator = userRepository.findById(operatorId)
			.orElseThrow(() -> new IllegalStateException("개발용 운영진 회원이 없습니다: userId=" + operatorId));
		Organization organization = organizationRepository.save(SeedFixtures.organization());
		return memberRepository.save(SeedFixtures.representative(organization, operator));
	}

}
