package com.mozip.domain.application.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.mozip.domain.organization.entity.Organization;
import com.mozip.domain.organization.entity.OrganizationScope;
import com.mozip.domain.organization.entity.OrganizationType;
import com.mozip.domain.posting.entity.Posting;
import com.mozip.domain.user.entity.User;

// 서류 결과 지정의 불변 조건 검증 (DB 없이)
class ApplicationDocResultTest {

	private final Organization organization = Organization.create("테스트 단체", OrganizationType.CLUB, null,
		OrganizationScope.CAMPUS, null, true, null, null);
	private final Posting posting = Posting.create(organization, "테스트 공고");
	private final User user = User.create("01000000000", "encoded", "지원자", false, LocalDateTime.now());

	@Test
	@DisplayName("제출한 지원서에 서류 결과를 지정하면 그 값으로 바뀌고 다른 값으로 다시 바꿀 수 있다")
	void decideDocResult() {
		Application application = submitted();
		assertThat(application.getDocResult()).isEqualTo(DocResult.PENDING);

		application.decideDocResult(DocResult.HOLD);
		assertThat(application.getDocResult()).isEqualTo(DocResult.HOLD);

		application.decideDocResult(DocResult.PASS);
		application.decideDocResult(DocResult.PASS);
		assertThat(application.getDocResult()).isEqualTo(DocResult.PASS);
	}

	@Test
	@DisplayName("서류 결과를 PENDING이나 null로 지정할 수 없다")
	void rejectsPending() {
		Application application = submitted();
		application.decideDocResult(DocResult.FAIL);

		assertThatThrownBy(() -> application.decideDocResult(DocResult.PENDING))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> application.decideDocResult(null))
			.isInstanceOf(IllegalArgumentException.class);
		assertThat(application.getDocResult()).isEqualTo(DocResult.FAIL);
	}

	@Test
	@DisplayName("제출하지 않은 지원서에는 서류 결과를 지정할 수 없다")
	void rejectsDraft() {
		Application draft = Application.create(posting, null, user);

		assertThatThrownBy(() -> draft.decideDocResult(DocResult.PASS))
			.isInstanceOf(IllegalArgumentException.class);
		assertThat(draft.getDocResult()).isEqualTo(DocResult.PENDING);
	}

	private Application submitted() {
		Application application = Application.create(posting, null, user);
		application.submit("{}");
		return application;
	}

}
