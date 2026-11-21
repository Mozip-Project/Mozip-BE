package com.mozip.domain.posting.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;

import com.mozip.domain.organization.entity.Organization;
import com.mozip.domain.organization.entity.OrganizationType;
import com.mozip.domain.posting.entity.Posting;
import com.mozip.domain.posting.entity.PostingStatus;

import jakarta.persistence.criteria.Join;

// postings(목록 조회) 필터를 조건별로 조합. null을 반환하면 PostingService에서 그 조건은 안 걸음
public final class PostingSpecifications {

	private PostingSpecifications() {
	}

	public static Specification<Posting> statusIn(PostingStatus... statuses) {
		return (root, query, builder) -> root.get("status").in(List.of(statuses));
	}

	public static Specification<Posting> keyword(String keyword) {
		if (keyword == null || keyword.isBlank()) {
			return null;
		}
		return (root, query, builder) -> builder.like(root.get("title"), "%" + keyword + "%");
	}

	public static Specification<Posting> schoolId(Long schoolId) {
		if (schoolId == null) {
			return null;
		}
		return (root, query, builder) -> {
			Join<Posting, Organization> organization = root.join("organization");
			return builder.equal(organization.get("school").get("id"), schoolId);
		};
	}

	public static Specification<Posting> organizationType(OrganizationType organizationType) {
		if (organizationType == null) {
			return null;
		}
		return (root, query, builder) -> {
			Join<Posting, Organization> organization = root.join("organization");
			return builder.equal(organization.get("type"), organizationType);
		};
	}

	public static Specification<Posting> hasInterview(Boolean hasInterview) {
		if (hasInterview == null) {
			return null;
		}
		return (root, query, builder) -> builder.equal(root.get("hasInterview"), hasInterview);
	}

	// 마감 3일 이내 (recruitEndAt이 지금부터 72시간 이내면서 아직 지나지 않음)
	public static Specification<Posting> closingSoon(Boolean closingSoon) {
		if (closingSoon == null || !closingSoon) {
			return null;
		}
		return (root, query, builder) -> {
			LocalDateTime now = LocalDateTime.now();
			return builder.between(root.get("recruitEndAt"), now, now.plusDays(3));
		};
	}

}
