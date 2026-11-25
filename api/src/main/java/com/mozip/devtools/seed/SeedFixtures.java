package com.mozip.devtools.seed;

import java.time.LocalDateTime;
import java.util.List;

import com.mozip.domain.organization.dto.CreateOrganizationInput;
import com.mozip.domain.organization.entity.OrganizationScope;
import com.mozip.domain.organization.entity.OrganizationType;
import com.mozip.domain.posting.dto.PositionInput;
import com.mozip.domain.posting.dto.PostingDraftInput;
import com.mozip.domain.posting.dto.QuestionInput;
import com.mozip.domain.posting.entity.QuestionType;

public final class SeedFixtures {

	public static final String ORGANIZATION_NAME = "개발용 단체";
	public static final String SCHOOL_NAME = "개발용 대학교";
	public static final String POSTING_TITLE = "개발용 모집 공고";

	public static final String DEVELOPER_POSITION_NAME = "개발";
	public static final String DESIGNER_POSITION_NAME = "디자인";

	// 서류 평가 화면에서 정렬과 검색을 바로 써 볼 수 있게 이름을 섞어 둠
	public static final List<String> APPLICANT_NAMES = List.of("김민준", "이서연", "박도윤", "최지우", "정하준",
		"강서윤", "조은우", "윤지호", "장수아", "임시우");

	private SeedFixtures() {
	}

	// 학교 데이터 없이 만들 수 있는 연합 동아리
	public static CreateOrganizationInput createOrganizationInput() {
		return new CreateOrganizationInput(ORGANIZATION_NAME, OrganizationType.CLUB, null, OrganizationScope.UNION,
			null, false, null, null);
	}

	// 개발용 회원(MockRole) 전화번호 01000000001~3과 겹치지 않는 대역
	public static String applicantPhone(int index) {
		return "010000001" + String.format("%02d", index + 1);
	}

	// 기본 정보와 포지션 단계를 한 번에 채움. 모집 기간은 지금 지원할 수 있도록 어제부터 30일 뒤까지
	public static PostingDraftInput postingBasicInput(LocalDateTime now) {
		return new PostingDraftInput(POSTING_TITLE, "서류 평가 화면을 확인하려고 넣어 둔 공고입니다.", "2026-2", "서울", "무료",
			true, true, null,
			null, null, now.minusDays(1), now.plusDays(30), null,
			false, List.of(new PositionInput(null, DEVELOPER_POSITION_NAME, 3, 0),
				new PositionInput(null, DESIGNER_POSITION_NAME, 2, 1)),
			null, null, null, null, null, null, null, null,
			null, null,
			null);
	}

	// 공통 문항 2개와 개발 포지션 문항 1개. 문항별 점수를 쓰는 공고로 둠
	public static PostingDraftInput postingQuestionsInput(Long developerPositionId) {
		return new PostingDraftInput(null, null, null, null, null, null, null, null,
			null, null, null, null, null,
			null, null,
			null, null, null, null, null, null, null, null,
			true, List.of(
				new QuestionInput(null, QuestionType.TEXT, "지원 동기를 적어 주세요", null, true, 500, null, 0),
				new QuestionInput(null, QuestionType.TEXT, "함께 해 본 활동을 적어 주세요", null, true, 500, null, 1),
				new QuestionInput(developerPositionId, QuestionType.TEXT, "써 본 기술을 적어 주세요", null, true, 500,
					null, 2)),
			null);
	}

}
