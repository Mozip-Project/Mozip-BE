package com.mozip.domain.organization.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mozip.domain.organization.dto.CreateOrganizationInput;
import com.mozip.domain.organization.dto.UpdateOrganizationInput;
import com.mozip.domain.organization.entity.Member;
import com.mozip.domain.organization.entity.MemberRole;
import com.mozip.domain.organization.entity.Organization;
import com.mozip.domain.organization.entity.OrganizationField;
import com.mozip.domain.organization.entity.OrganizationFieldTag;
import com.mozip.domain.organization.entity.OrganizationKeyword;
import com.mozip.domain.organization.entity.OrganizationLink;
import com.mozip.domain.organization.entity.OrganizationLinkType;
import com.mozip.domain.organization.entity.OrganizationScope;
import com.mozip.domain.organization.entity.OrganizationType;
import com.mozip.domain.organization.entity.ReviewStatus;
import com.mozip.domain.organization.exception.OrganizationErrorCode;
import com.mozip.domain.organization.exception.OrganizationInputReason;
import com.mozip.domain.organization.repository.MemberRepository;
import com.mozip.domain.organization.repository.OrganizationFieldTagRepository;
import com.mozip.domain.organization.repository.OrganizationKeywordRepository;
import com.mozip.domain.organization.repository.OrganizationLinkRepository;
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

	private static final int KEYWORD_SUGGESTION_LIMIT = 10;
	// 키워드와 링크 컬럼 길이 (DDL과 같음)
	private static final int KEYWORD_MAX_LENGTH = 20;
	private static final int LINK_MAX_LENGTH = 500;

	private final OrganizationRepository organizationRepository;
	private final MemberRepository memberRepository;
	private final OrganizationKeywordRepository keywordRepository;
	private final OrganizationFieldTagRepository fieldTagRepository;
	private final OrganizationLinkRepository linkRepository;
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

	// 단체 프로필 수정, 그 단체 운영진만 가능. 키워드/분야/링크는 보낸 목록으로 전체 교체
	@Transactional
	public Organization updateOrganization(Long userId, Long organizationId, UpdateOrganizationInput input) {
		// 같은 단체 동시 수정은 단체 행을 잠가 차례로 처리
		organizationRepository.lockById(organizationId);
		Organization organization = getOrganization(organizationId);
		if (!memberRepository.existsByOrganizationIdAndUserIdAndLeftAtIsNull(organizationId, userId)) {
			throw new BusinessException(CommonErrorCode.FORBIDDEN)
				.addContext("organizationId", organizationId)
				.addContext("userId", userId);
		}
		validateFields(organization, input.fields());
		List<OrganizationLink> links = toLinks(organization, input.links());

		organization.updateProfile(blankToNull(input.logoUrl()), input.tagline(), input.description(),
			blankToNull(input.email()));

		fieldTagRepository.deleteAllByOrganization(organization);
		fieldTagRepository.saveAll(new LinkedHashSet<>(input.fields()).stream()
			.map(field -> OrganizationFieldTag.create(organization, field))
			.toList());

		keywordRepository.deleteAllByOrganization(organization);
		keywordRepository.saveAll(normalizeKeywords(input.keywords()).stream()
			.map(keyword -> OrganizationKeyword.create(organization, keyword))
			.toList());

		linkRepository.deleteAllByOrganization(organization);
		linkRepository.saveAll(links);
		return organization;
	}

	// 심사 승인된 단체 키워드 중 접두 일치 상위 10개 (많이 쓰인 순, 같으면 사전순)
	public List<String> getKeywordSuggestions(String prefix) {
		String trimmed = prefix == null ? "" : prefix.strip();
		if (trimmed.isEmpty() || trimmed.length() > KEYWORD_MAX_LENGTH) {
			return List.of();
		}
		return keywordRepository.findKeywordsByPrefix(escapeLike(trimmed) + "%", ReviewStatus.APPROVED,
			PageRequest.of(0, KEYWORD_SUGGESTION_LIMIT));
	}

	// 운영진 홈 드롭다운에서 단체 선택, 없으면 ORG_404이고 소속이 아니면 COMMON_403
	@Transactional
	public User selectOrganization(Long userId, Long organizationId) {
		Organization organization = getOrganization(organizationId);
		if (!memberRepository.existsByOrganizationIdAndUserIdAndLeftAtIsNull(organizationId, userId)) {
			throw new BusinessException(CommonErrorCode.FORBIDDEN)
				.addContext("organizationId", organizationId)
				.addContext("userId", userId);
		}
		User user = userRepository.findById(userId)
			.orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND).addContext("userId", userId));
		user.selectOrganization(organization);
		return user;
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
			&& (userId == null
			|| !memberRepository.existsByOrganizationIdAndUserIdAndLeftAtIsNull(organizationId, userId))) {
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

	// 학생회가 아니면 활동 분야 1개 이상, 학생회는 활동 분야 없음
	private static void validateFields(Organization organization, List<OrganizationField> fields) {
		if (organization.isStudentCouncil() && !fields.isEmpty()) {
			throw invalid(OrganizationInputReason.FIELDS_NOT_ALLOWED);
		}
		if (!organization.isStudentCouncil() && fields.isEmpty()) {
			throw invalid(OrganizationInputReason.FIELDS_REQUIRED);
		}
	}

	// 앞뒤 공백을 지우고 같은 키워드는 한 번만 남김 (순서 유지)
	private static Set<String> normalizeKeywords(List<String> keywords) {
		Set<String> normalized = new LinkedHashSet<>();
		for (String keyword : keywords) {
			normalized.add(keyword.strip());
		}
		return normalized;
	}

	// 스킴이 없으면 https://를 붙이고 호스트로 type을 추정
	// http, https가 아니거나 https://를 붙여 컬럼 길이를 넘으면 거부
	private static List<OrganizationLink> toLinks(Organization organization, List<String> urls) {
		Set<String> seen = new LinkedHashSet<>();
		for (String raw : urls) {
			String url = raw.strip();
			if (!url.contains("://")) {
				url = "https://" + url;
			}
			if (!isHttp(url) || url.length() > LINK_MAX_LENGTH) {
				throw invalid(OrganizationInputReason.LINK_URL_INVALID).addContext("url", url);
			}
			seen.add(url);
		}
		return seen.stream()
			.map(url -> OrganizationLink.create(organization, OrganizationLinkType.fromHost(hostOf(url)), url))
			.toList();
	}

	private static String hostOf(String url) {
		try {
			String host = new URI(url).getHost();
			if (host == null || host.isBlank()) {
				throw invalid(OrganizationInputReason.LINK_URL_INVALID).addContext("url", url);
			}
			return host;
		} catch (URISyntaxException e) {
			throw invalid(OrganizationInputReason.LINK_URL_INVALID).addContext("url", url);
		}
	}

	private static boolean isHttp(String url) {
		String lower = url.toLowerCase(Locale.ROOT);
		return lower.startsWith("http://") || lower.startsWith("https://");
	}

	// 빈 문자열은 null로 저장
	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.strip();
	}

	// like 패턴 문자를 !로 이스케이프
	private static String escapeLike(String value) {
		return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
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
