package com.mozip.domain.posting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import com.mozip.TestcontainersConfiguration;
import com.mozip.domain.organization.entity.Organization;
import com.mozip.domain.organization.entity.OrganizationScope;
import com.mozip.domain.organization.entity.OrganizationType;
import com.mozip.domain.organization.repository.OrganizationRepository;
import com.mozip.domain.posting.entity.Posting;
import com.mozip.domain.posting.entity.PostingStatus;
import com.mozip.domain.posting.entity.ReportReason;
import com.mozip.domain.posting.exception.PostingErrorCode;
import com.mozip.domain.posting.exception.ReportErrorCode;
import com.mozip.domain.posting.repository.PostingRepository;
import com.mozip.domain.posting.repository.ReportRepository;
import com.mozip.domain.user.entity.User;
import com.mozip.domain.user.repository.UserRepository;
import com.mozip.global.exception.BusinessException;
import com.mozip.global.exception.CommonErrorCode;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class ReportServiceTest {

	@Autowired
	private ReportService reportService;

	@Autowired
	private ReportRepository reportRepository;

	@Autowired
	private OrganizationRepository organizationRepository;

	@Autowired
	private PostingRepository postingRepository;

	@Autowired
	private UserRepository userRepository;

	private Organization organization;
	private User user;
	private Posting posting;

	@BeforeEach
	void setUp() {
		organization = organizationRepository.save(
			Organization.create("테스트 단체", OrganizationType.CLUB, OrganizationScope.CAMPUS, null, false));
		user = userRepository.save(User.create("01066660001", "encoded", "지원자", false, LocalDateTime.now()));
		// 이 브랜치에는 아직 공고 상태 전환 API가 없어(다른 이슈 몫) 테스트에서 직접 RECRUITING으로 바꿔서
		// "신고 가능한, 게시된 공고" 전제를 맞춤. DRAFT 거부 자체는 rejectsReportingDraftPosting에서 따로 확인
		posting = postingRepository.save(Posting.create(organization, "공고", false, false));
		ReflectionTestUtils.setField(posting, "status", PostingStatus.RECRUITING);
		postingRepository.flush();
	}

	@Test
	@DisplayName("신고하면 Report가 저장되고 true를 돌려준다")
	void reportsSuccessfully() {
		boolean result = reportService.report(user.getId(), posting.getId(), ReportReason.INAPPROPRIATE, "부적절해요");

		assertThat(result).isTrue();
		assertThat(reportRepository.existsByUserAndPosting(user, posting)).isTrue();
	}

	@Test
	@DisplayName("같은 회원이 같은 공고를 두 번 신고하면 REPORT_409를 던진다")
	void rejectsDuplicateReport() {
		reportService.report(user.getId(), posting.getId(), ReportReason.FALSE_INFO, null);

		assertThatThrownBy(() -> reportService.report(user.getId(), posting.getId(), ReportReason.ETC, "또 신고"))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException)e).getErrorCode())
			.isEqualTo(ReportErrorCode.CONFLICT);
	}

	@Test
	@DisplayName("다른 회원은 같은 공고를 각자 신고할 수 있다")
	void allowsDifferentUsersToReportSamePosting() {
		User other = userRepository.save(User.create("01066660002", "encoded", "지원자2", false, LocalDateTime.now()));
		reportService.report(user.getId(), posting.getId(), ReportReason.FALSE_INFO, null);

		boolean result = reportService.report(other.getId(), posting.getId(), ReportReason.FALSE_INFO, null);

		assertThat(result).isTrue();
	}

	@Test
	@DisplayName("없는 공고를 신고하면 POSTING_404를 던진다")
	void rejectsReportingMissingPosting() {
		assertThatThrownBy(() -> reportService.report(user.getId(), 999_999L, ReportReason.ETC, null))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException)e).getErrorCode())
			.isEqualTo(PostingErrorCode.NOT_FOUND);
	}

	@Test
	@DisplayName("아직 게시 전(DRAFT)인 공고를 신고하면 POSTING_404를 던진다")
	void rejectsReportingDraftPosting() {
		Posting draft = postingRepository.save(Posting.create(organization, "초안 공고", false, false));

		assertThatThrownBy(() -> reportService.report(user.getId(), draft.getId(), ReportReason.ETC, null))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException)e).getErrorCode())
			.isEqualTo(PostingErrorCode.NOT_FOUND);
	}

	@Test
	@DisplayName("detail이 300자를 넘으면 COMMON_400을 던진다")
	void rejectsReportWithTooLongDetail() {
		String tooLong = "가".repeat(301);

		assertThatThrownBy(() -> reportService.report(user.getId(), posting.getId(), ReportReason.ETC, tooLong))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException)e).getErrorCode())
			.isEqualTo(CommonErrorCode.INVALID_INPUT);
	}

}
