package com.mozip.domain.posting.controller;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.stereotype.Controller;

import com.mozip.domain.organization.entity.Organization;
import com.mozip.domain.organization.repository.OrganizationRepository;
import com.mozip.domain.posting.entity.Posting;

import lombok.RequiredArgsConstructor;

// 공고 조회/작성 API는 아직 이 브랜치에 없어서(다른 이슈 몫), myScraps가 돌려주는 Posting의 자식 필드 중
// 엔티티에 이미 있는 organization만 N+1 없이 풀어줌. 나머지 필드는 Unmapped fields로 남음
@Controller
@RequiredArgsConstructor
public class PostingGraphqlController {

	private final OrganizationRepository organizationRepository;

	@BatchMapping(typeName = "Posting", field = "organization")
	public Map<Posting, Organization> organization(List<Posting> postings) {
		List<Long> organizationIds = postings.stream()
			.map(posting -> posting.getOrganization().getId())
			.distinct()
			.toList();
		Map<Long, Organization> organizationsById = organizationRepository.findAllById(organizationIds).stream()
			.collect(Collectors.toMap(Organization::getId, Function.identity()));
		return postings.stream()
			.collect(Collectors.toMap(Function.identity(),
				posting -> organizationsById.get(posting.getOrganization().getId())));
	}

}
