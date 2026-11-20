package com.mozip.devtools.seed;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.mozip.devtools.authbypass.MockRole;
import com.mozip.devtools.authbypass.MockUserRegistry;
import com.mozip.domain.organization.entity.Member;
import com.mozip.domain.organization.entity.MemberRole;
import com.mozip.domain.organization.entity.Organization;
import com.mozip.domain.organization.repository.MemberRepository;
import com.mozip.domain.organization.service.OrganizationService;
import com.mozip.global.security.ConditionalOnAuthBypass;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// 인증 우회 중 앱 시작 시 개발용 OPERATOR를 개발용 단체의 대표자로 등록 (이미 소속 단체가 있으면 재사용)
// 실제 단체 생성과 같은 경로(OrganizationService)를 거치기 때문에 대표자 등록, 무료 체험 시작까지 같이 진행
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
	private final MemberRepository memberRepository;
	private final OrganizationService organizationService;

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		Long operatorId = mockUserRegistry.get(MockRole.OPERATOR).userId();
		// 다른 단체에 운영진으로만 들어가 있어도 시드 단체를 따로 만들도록 대표자인 시드 단체만 재사용
		Organization organization = memberRepository.findAllByUserId(operatorId).stream()
			.filter(member -> member.getRole() == MemberRole.REPRESENTATIVE)
			.map(Member::getOrganization)
			.filter(candidate -> SeedFixtures.ORGANIZATION_NAME.equals(candidate.getName()))
			.findFirst()
			.orElseGet(() -> organizationService.createOrganization(operatorId, SeedFixtures.createOrganizationInput()));
		log.warn("개발용 단체 준비: organizationId={}, representativeUserId={}", organization.getId(), operatorId);
	}

}
