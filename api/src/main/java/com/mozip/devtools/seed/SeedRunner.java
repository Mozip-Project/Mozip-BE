package com.mozip.devtools.seed;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.mozip.devtools.authbypass.MockRole;
import com.mozip.devtools.authbypass.MockUserRegistry;
import com.mozip.domain.application.dto.AnswerInput;
import com.mozip.domain.application.dto.ApplicationInput;
import com.mozip.domain.application.entity.Application;
import com.mozip.domain.application.service.ApplicationService;
import com.mozip.domain.organization.entity.Member;
import com.mozip.domain.organization.entity.MemberRole;
import com.mozip.domain.organization.entity.Organization;
import com.mozip.domain.organization.repository.MemberRepository;
import com.mozip.domain.organization.service.OrganizationService;
import com.mozip.domain.posting.entity.Position;
import com.mozip.domain.posting.entity.Posting;
import com.mozip.domain.posting.entity.PostingStatus;
import com.mozip.domain.posting.entity.Question;
import com.mozip.domain.posting.repository.PositionRepository;
import com.mozip.domain.posting.repository.PostingRepository;
import com.mozip.domain.posting.repository.QuestionRepository;
import com.mozip.domain.posting.service.PostingService;
import com.mozip.domain.school.entity.School;
import com.mozip.domain.school.repository.SchoolRepository;
import com.mozip.domain.user.entity.User;
import com.mozip.domain.user.repository.UserRepository;
import com.mozip.global.config.graphql.LocalDateTimeScalar;
import com.mozip.global.security.ConditionalOnAuthBypass;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// 인증 우회 중 앱 시작 시 개발용 OPERATOR를 대표자로 하는 개발용 단체를 만듦 (이미 있으면 재사용)
// 그 단체에 모집 중 공고 1개와 제출된 지원서 10개도 같이 넣어 서류 평가 화면을 바로 볼 수 있게 함
// TODO(A 이용권): 이용권 모델이 들어오면 개발용 단체에 무제한 이용권을 같이 넣어야 ORG_402에 막히지 않음
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnAuthBypass
@Order(SeedRunner.ORDER)
public class SeedRunner implements ApplicationRunner {

	public static final int ORDER = MockUserRegistry.ORDER + 1;

	private final MockUserRegistry mockUserRegistry;
	private final MemberRepository memberRepository;
	private final OrganizationService organizationService;
	private final PostingRepository postingRepository;
	private final PositionRepository positionRepository;
	private final QuestionRepository questionRepository;
	private final SchoolRepository schoolRepository;
	private final UserRepository userRepository;
	private final PostingService postingService;
	private final ApplicationService applicationService;
	private final PasswordEncoder passwordEncoder;

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		Long operatorId = mockUserRegistry.get(MockRole.OPERATOR).userId();
		// 대표자로 있는 단체만 재사용
		Organization organization = memberRepository.findAllByUserId(operatorId).stream()
			.filter(member -> member.getRole() == MemberRole.REPRESENTATIVE)
			.map(Member::getOrganization)
			.filter(candidate -> SeedFixtures.ORGANIZATION_NAME.equals(candidate.getName()))
			.findFirst()
			.orElseGet(() -> organizationService.createOrganization(operatorId, SeedFixtures.createOrganizationInput()));
		log.warn("개발용 단체 준비: organizationId={}, representativeUserId={}", organization.getId(), operatorId);

		seedPostingWithApplications(operatorId, organization);
	}

	// 같은 제목의 공고가 단체에 이미 있으면 아무것도 만들지 않음. 재기동해도 공고와 지원서가 쌓이지 않음
	private void seedPostingWithApplications(Long operatorId, Organization organization) {
		boolean alreadySeeded = postingRepository
			.findAllByOrganizationAndStatusInOrderByCreatedAtDesc(organization, List.of(PostingStatus.values()))
			.stream()
			.anyMatch(posting -> SeedFixtures.POSTING_TITLE.equals(posting.getTitle()));
		if (alreadySeeded) {
			return;
		}

		// 모집 기간 판정(ApplicationService)이 서울 시각 기준이라 같은 기준으로 맞춤
		LocalDateTime now = LocalDateTime.now(LocalDateTimeScalar.ZONE);
		Posting posting = postingService.saveDraft(operatorId, organization.getId(), null,
			SeedFixtures.postingBasicInput(now));
		List<Position> positions = positionRepository.findAllByPostingOrderBySortOrder(posting);
		Position developer = positions.get(0);
		postingService.saveDraft(operatorId, organization.getId(), posting.getId(),
			SeedFixtures.postingQuestionsInput(developer.getId()));
		postingService.publish(operatorId, posting.getId());

		List<Question> questions = questionRepository.findAllByPostingOrderBySortOrder(posting);
		School school = findOrCreateSchool();
		for (int i = 0; i < SeedFixtures.APPLICANT_NAMES.size(); i++) {
			User applicant = findOrCreateApplicant(i, school);
			Position position = positions.get(i % positions.size());
			Application application = applicationService.saveDraft(applicant.getId(), posting.getId(),
				new ApplicationInput(position.getId(), answers(questions, position, applicant), null));
			applicationService.submit(applicant.getId(), application.getId());
		}
		log.warn("개발용 공고 준비: postingId={}, applications={}", posting.getId(), SeedFixtures.APPLICANT_NAMES.size());
	}

	private School findOrCreateSchool() {
		return schoolRepository.findAll().stream()
			.filter(school -> SeedFixtures.SCHOOL_NAME.equals(school.getName()))
			.findFirst()
			.orElseGet(() -> schoolRepository.save(School.create(SeedFixtures.SCHOOL_NAME, null)));
	}

	// 지원서 제출은 학교 인증을 마친 회원만 할 수 있어 개발용 학교로 인증해 둠
	private User findOrCreateApplicant(int index, School school) {
		String phone = SeedFixtures.applicantPhone(index);
		User applicant = userRepository.findByPhone(phone)
			.orElseGet(() -> userRepository.save(User.create(phone, passwordEncoder.encode(UUID.randomUUID().toString()),
				SeedFixtures.APPLICANT_NAMES.get(index), false, LocalDateTime.now())));
		if (applicant.getSchool() == null) {
			applicant.verifySchool(school);
		}
		return applicant;
	}

	// 공통 문항과 지원한 포지션의 문항에만 답함
	private List<AnswerInput> answers(List<Question> questions, Position position, User applicant) {
		List<AnswerInput> answers = new ArrayList<>();
		for (Question question : questions) {
			if (question.getPosition() != null && !question.getPosition().getId().equals(position.getId())) {
				continue;
			}
			answers.add(new AnswerInput(question.getId(),
				applicant.getName() + "의 답변입니다. " + question.getTitle(), null));
		}
		return answers;
	}

}
