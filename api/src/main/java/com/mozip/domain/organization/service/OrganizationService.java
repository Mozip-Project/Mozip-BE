package com.mozip.domain.organization.service;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mozip.domain.organization.dto.CreateOrganizationInput;
import com.mozip.domain.organization.entity.Member;
import com.mozip.domain.organization.entity.MemberRole;
import com.mozip.domain.organization.entity.Organization;
import com.mozip.domain.organization.entity.OrganizationScope;
import com.mozip.domain.organization.entity.OrganizationType;
import com.mozip.domain.organization.entity.ReviewStatus;
import com.mozip.domain.organization.exception.OrganizationErrorCode;
import com.mozip.domain.organization.exception.OrganizationInputReason;
import com.mozip.domain.organization.repository.MemberRepository;
import com.mozip.domain.organization.repository.OrganizationRepository;
import com.mozip.domain.payment.service.SubscriptionService;
import com.mozip.domain.school.entity.School;
import com.mozip.domain.school.repository.SchoolRepository;
import com.mozip.domain.user.entity.User;
import com.mozip.domain.user.repository.UserRepository;
import com.mozip.global.exception.BusinessException;
import com.mozip.global.exception.CommonErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrganizationService {

	private final OrganizationRepository organizationRepository;
	private final MemberRepository memberRepository;
	private final UserRepository userRepository;
	private final SchoolRepository schoolRepository;
	private final SubscriptionService subscriptionService;

	// 단체를 만들고 호출자를 대표자로 등록, 같은 요청이 두 번 오면 단체가 두 개 생김 (중복 제출은 프론트에서 막음)
	@Transactional
	public Organization createOrganization(Long userId, CreateOrganizationInput input) {
		validate(input);
		User user = userRepository.findById(userId)
			.orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND).addContext("userId", userId));
		School school = findSchool(input.schoolId());
		Organization organization = organizationRepository.save(Organization.create(input.name(), input.type(),
			input.councilLevel(), input.scope(), school, input.noTeam(), input.proofLink(), input.proofFileUrl()));
		memberRepository.save(Member.create(organization, user, null, MemberRole.REPRESENTATIVE));
		subscriptionService.startFreeTrial(organization.getId());
		return organization;
	}

	// 내가 운영진으로 속한 단체 목록
	public List<Organization> getMyOrganizations(Long userId) {
		return organizationRepository.findAllByMemberUserId(userId);
	}

	public Organization getOrganization(Long organizationId) {
		return organizationRepository.findWithSchoolById(organizationId)
			.orElseThrow(() -> new BusinessException(OrganizationErrorCode.ORGANIZATION_NOT_FOUND)
				.addContext("organizationId", organizationId));
	}

	// 심사 전이거나 반려된 단체는 소속 운영진에게만 보이고, 그 외에는 ORG_404
	public Organization getVisibleOrganization(Long organizationId, Long userId) {
		Organization organization = getOrganization(organizationId);
		if (organization.getReviewStatus() != ReviewStatus.APPROVED
			&& (userId == null || !memberRepository.existsByOrganizationIdAndUserId(organizationId, userId))) {
			throw new BusinessException(OrganizationErrorCode.ORGANIZATION_NOT_FOUND)
				.addContext("organizationId", organizationId);
		}
		return organization;
	}

	// 운영진 전용 필드(members, 심사 증빙)를 볼 수 있는 단체 id
	public Set<Long> getManageableOrganizationIds(Long userId, Collection<Organization> organizations) {
		if (userId == null || organizations.isEmpty()) {
			return Set.of();
		}
		return Set.copyOf(memberRepository.findOrganizationIdsByUserIdAndOrganizationIn(userId, organizations));
	}

	private static void validate(CreateOrganizationInput input) {
		boolean council = input.type() == OrganizationType.STUDENT_COUNCIL;
		if (council && input.councilLevel() == null) {
			throw invalid(OrganizationInputReason.COUNCIL_LEVEL_REQUIRED);
		}
		if (!council && input.councilLevel() != null) {
			throw invalid(OrganizationInputReason.COUNCIL_LEVEL_NOT_ALLOWED);
		}
		boolean union = input.scope() == OrganizationScope.UNION;
		if (union && input.schoolId() != null) {
			throw invalid(OrganizationInputReason.SCHOOL_NOT_ALLOWED);
		}
		if (!union && input.schoolId() == null) {
			throw invalid(OrganizationInputReason.SCHOOL_REQUIRED);
		}
	}

	private School findSchool(Long schoolId) {
		if (schoolId == null) {
			return null;
		}
		return schoolRepository.findById(schoolId)
			.orElseThrow(() -> invalid(OrganizationInputReason.SCHOOL_NOT_FOUND).addContext("schoolId", schoolId));
	}

	private static BusinessException invalid(OrganizationInputReason reason) {
		return new BusinessException(CommonErrorCode.INVALID_INPUT, reason);
	}

}
