package com.mozip.domain.posting.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Iterator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

// 정렬 기준 값이 같은 공고가 있을 때 페이지 경계에서 순서가 흔들리지 않도록 id를 붙였는지 확인 (DB 없이)
class PostingSortTest {

	@Test
	@DisplayName("모든 정렬 기준은 id DESC를 타이브레이커로 붙인다")
	void appendsIdAsTiebreaker() {
		for (PostingSort sort : PostingSort.values()) {
			Iterator<Sort.Order> orders = sort.toJpaSort().iterator();
			orders.next();
			assertThat(orders.hasNext()).isTrue();
			Sort.Order tiebreaker = orders.next();
			assertThat(tiebreaker.getProperty()).isEqualTo("id");
			assertThat(tiebreaker.getDirection()).isEqualTo(Sort.Direction.DESC);
			assertThat(orders.hasNext()).isFalse();
		}
	}

	@Test
	@DisplayName("CLOSING_SOON은 recruitEndAt이 null이면 nullsLast로 뒤로 보낸다")
	void closingSoonPutsNullRecruitEndAtLast() {
		Sort.Order primary = PostingSort.CLOSING_SOON.toJpaSort().iterator().next();
		assertThat(primary.getProperty()).isEqualTo("recruitEndAt");
		assertThat(primary.getDirection()).isEqualTo(Sort.Direction.ASC);
		assertThat(primary.getNullHandling()).isEqualTo(Sort.NullHandling.NULLS_LAST);
	}

}
