package com.mozip.domain.user.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mozip.domain.organization.entity.Member;
import com.mozip.domain.organization.entity.Organization;
import com.mozip.domain.organization.exception.OrganizationErrorCode;
import com.mozip.domain.organization.repository.MemberRepository;
import com.mozip.domain.organization.repository.OrganizationRepository;
import com.mozip.domain.user.dto.Me;
import com.mozip.domain.user.dto.NotificationSettings;
import com.mozip.domain.user.entity.Role;
import com.mozip.domain.user.entity.User;
import com.mozip.domain.user.exception.UserErrorCode;
import com.mozip.domain.user.repository.UserRepository;
import com.mozip.global.exception.BusinessException;
import com.mozip.global.exception.CommonErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

	private final UserRepository userRepository;
	private final OrganizationRepository organizationRepository;
	private final MemberRepository memberRepository;

	@Transactional(readOnly = true)
	public Me me(Long userId) {
		return toMe(findUser(userId));
	}

	@Transactional
	public Me switchRole(Long userId, Role role, Long organizationId) {
		User user = findUser(userId);
		if (role == Role.OPERATOR) {
			Organization organization = findOperatingOrganization(userId, organizationId);
			user.updateLastOrganization(organization);
		}
		user.updateLastRole(role);
		return toMe(user);
	}

	// organizationId가 없거나, 요청한 회원이 그 단체의 운영진이 아니면 거부
	// TODO: isOperatorOf는 별도 담당자가 구현 예정. 지금은 MemberRepository로 임시 대체
	private Organization findOperatingOrganization(Long userId, Long organizationId) {
		if (organizationId == null) {
			throw new BusinessException(CommonErrorCode.INVALID_INPUT)
				.addContext("reason", "organizationId is required for OPERATOR");
		}
		Organization organization = organizationRepository.findByIdWithSchool(organizationId)
			.orElseThrow(() -> new BusinessException(OrganizationErrorCode.NOT_FOUND));
		if (!memberRepository.existsByOrganizationIdAndUserId(organizationId, userId)) {
			throw new BusinessException(CommonErrorCode.FORBIDDEN).addContext("userId", userId);
		}
		return organization;
	}

	private User findUser(Long userId) {
		return userRepository.findByIdWithAssociations(userId)
			.orElseThrow(() -> new BusinessException(UserErrorCode.NOT_FOUND));
	}

	private Me toMe(User user) {
		List<Organization> organizations = memberRepository.findByUserId(user.getId()).stream()
			.map(Member::getOrganization)
			.toList();

		return new Me(
			user.getId(),
			user.getPhone(),
			user.getName(),
			user.getAuthority(),
			user.getStatus(),
			user.getLastRole(),
			user.getLastOrganization(),
			user.getSchool(),
			user.isMarketingAgreed(),
			user.getTermsAgreedAt(),
			organizations,
			NotificationSettings.defaults(),
			0,
			user.getCreatedAt()
		);
	}

}
