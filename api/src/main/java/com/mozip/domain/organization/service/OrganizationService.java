package com.mozip.domain.organization.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mozip.domain.file.dto.FilePurpose;
import com.mozip.domain.organization.entity.Organization;
import com.mozip.domain.organization.exception.OrganizationErrorCode;
import com.mozip.domain.organization.repository.MemberRepository;
import com.mozip.domain.organization.repository.OrganizationRepository;
import com.mozip.global.config.S3Properties;
import com.mozip.global.exception.BusinessException;
import com.mozip.global.exception.CommonErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrganizationService {

	private final OrganizationRepository organizationRepository;
	private final MemberRepository memberRepository;
	private final S3Properties s3Properties;

	@Transactional
	public Organization updateLogo(Long organizationId, String fileKey, Long requesterId) {
		// 발급 당시 purpose와 다른 fileKey가 들어오는 것을 막음 (예: 다른 purpose로 발급받은 키 재사용)
		if (!fileKey.startsWith(FilePurpose.ORG_LOGO.keyPrefix() + "/")) {
			throw new BusinessException(CommonErrorCode.INVALID_INPUT).addContext("fileKey", fileKey);
		}
		Organization organization = organizationRepository.findByIdWithSchool(organizationId)
			.orElseThrow(() -> new BusinessException(OrganizationErrorCode.NOT_FOUND));
		if (!memberRepository.existsByOrganizationIdAndUserId(organizationId, requesterId)) {
			throw new BusinessException(OrganizationErrorCode.FORBIDDEN).addContext("userId", requesterId);
		}
		organization.updateLogo(s3Properties.cdnDomain() + "/" + fileKey);
		return organization;
	}

}
