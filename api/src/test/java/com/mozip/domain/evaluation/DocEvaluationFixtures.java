package com.mozip.domain.evaluation;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.beans.factory.BeanFactory;

import com.mozip.domain.application.entity.Application;
import com.mozip.domain.application.repository.ApplicationRepository;
import com.mozip.domain.evaluation.entity.DocEvaluation;
import com.mozip.domain.evaluation.entity.DocScore;
import com.mozip.domain.evaluation.entity.Opinion;
import com.mozip.domain.evaluation.repository.DocEvaluationRepository;
import com.mozip.domain.organization.entity.Member;
import com.mozip.domain.organization.entity.MemberRole;
import com.mozip.domain.organization.entity.Organization;
import com.mozip.domain.organization.entity.OrganizationScope;
import com.mozip.domain.organization.entity.OrganizationType;
import com.mozip.domain.organization.repository.MemberRepository;
import com.mozip.domain.organization.repository.OrganizationRepository;
import com.mozip.domain.posting.entity.Position;
import com.mozip.domain.posting.entity.Posting;
import com.mozip.domain.posting.entity.PostingStatus;
import com.mozip.domain.posting.entity.Question;
import com.mozip.domain.posting.entity.QuestionType;
import com.mozip.domain.posting.repository.PositionRepository;
import com.mozip.domain.posting.repository.PostingRepository;
import com.mozip.domain.posting.repository.QuestionRepository;
import com.mozip.domain.user.entity.User;
import com.mozip.domain.user.repository.UserRepository;

// 서류 평가 취합 테스트(#91)가 같이 쓰는 데이터 준비 도우미
// 공고 작성과 지원서 제출 흐름을 거치지 않고 엔티티를 바로 저장함. 메서드마다 저장까지 끝내서 트랜잭션 없는 HTTP 테스트에서도 그대로 씀
public class DocEvaluationFixtures {

	private final UserRepository userRepository;
	private final OrganizationRepository organizationRepository;
	private final MemberRepository memberRepository;
	private final PostingRepository postingRepository;
	private final PositionRepository positionRepository;
	private final QuestionRepository questionRepository;
	private final ApplicationRepository applicationRepository;
	private final DocEvaluationRepository docEvaluationRepository;

	public DocEvaluationFixtures(BeanFactory beans) {
		this.userRepository = beans.getBean(UserRepository.class);
		this.organizationRepository = beans.getBean(OrganizationRepository.class);
		this.memberRepository = beans.getBean(MemberRepository.class);
		this.postingRepository = beans.getBean(PostingRepository.class);
		this.positionRepository = beans.getBean(PositionRepository.class);
		this.questionRepository = beans.getBean(QuestionRepository.class);
		this.applicationRepository = beans.getBean(ApplicationRepository.class);
		this.docEvaluationRepository = beans.getBean(DocEvaluationRepository.class);
	}

	// 전화번호가 unique라 테스트마다 임의로 지음
	public User user(String name) {
		String phone = "0108" + String.format("%07d", ThreadLocalRandom.current().nextInt(10_000_000));
		return userRepository.save(User.create(phone, "encoded", name, false, LocalDateTime.now()));
	}

	public Organization organization() {
		String name = "취합 테스트 단체 " + ThreadLocalRandom.current().nextInt(100_000_000);
		return organizationRepository.save(Organization.create(name, OrganizationType.CLUB, null,
			OrganizationScope.CAMPUS, null, true, null, null));
	}

	public Member member(Organization organization, User user, MemberRole role) {
		return memberRepository.save(Member.create(organization, user, null, role));
	}

	// 서류 평가 단계(DOC_REVIEW)까지 온 공고
	public Posting posting(Organization organization, boolean docScorePerQuestion) {
		Posting posting = Posting.create(organization, "취합 테스트 공고 " + System.nanoTime());
		posting.updateQuestionsStep(docScorePerQuestion);
		posting.advanceStatus(PostingStatus.DOC_REVIEW);
		return postingRepository.save(posting);
	}

	// 아직 모집 중(RECRUITING)인 공고
	public Posting recruitingPosting(Organization organization) {
		Posting posting = Posting.create(organization, "모집 중 테스트 공고 " + System.nanoTime());
		posting.advanceStatus(PostingStatus.RECRUITING);
		return postingRepository.save(posting);
	}

	public Position position(Posting posting, String name, int sortOrder) {
		return positionRepository.save(Position.create(posting, null, name, null, sortOrder));
	}

	public Question question(Posting posting, int sortOrder) {
		return questionRepository.save(Question.create(posting, null, QuestionType.TEXT, "문항 " + sortOrder, null,
			true, 500, null, null, null, null, sortOrder));
	}

	// 제출된 지원서. 지원자 회원도 같이 만듦
	public Application submitted(Posting posting, Position position, String applicantName) {
		Application application = Application.create(posting, position, user(applicantName));
		application.submit("{}");
		return applicationRepository.save(application);
	}

	public Application draft(Posting posting, Position position, String applicantName) {
		return applicationRepository.save(Application.create(posting, position, user(applicantName)));
	}

	// 저장 버튼까지 누른(SAVED) 평가
	public DocEvaluation saved(Application application, Member member, Opinion opinion, List<DocScore> scores) {
		DocEvaluation evaluation = DocEvaluation.create(application, member);
		evaluation.submit(opinion, scores, opinion + " 의견 메모", LocalDateTime.now());
		return docEvaluationRepository.save(evaluation);
	}

	// 자동 저장만 된(DRAFT) 평가
	public DocEvaluation drafted(Application application, Member member, Opinion opinion, List<DocScore> scores) {
		DocEvaluation evaluation = DocEvaluation.create(application, member);
		evaluation.saveDraft(opinion, scores, null);
		return docEvaluationRepository.save(evaluation);
	}

}
