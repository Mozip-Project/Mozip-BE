package com.mozip.domain.evaluation.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.mozip.TestcontainersConfiguration;
import com.mozip.domain.application.entity.Application;
import com.mozip.domain.application.repository.ApplicationRepository;
import com.mozip.domain.evaluation.entity.DocEvaluation;
import com.mozip.domain.evaluation.entity.DocEvaluationStatus;
import com.mozip.domain.evaluation.entity.DocScore;
import com.mozip.domain.evaluation.entity.Opinion;
import com.mozip.domain.organization.entity.Member;
import com.mozip.domain.organization.entity.MemberRole;
import com.mozip.domain.organization.entity.Organization;
import com.mozip.domain.organization.entity.OrganizationScope;
import com.mozip.domain.organization.entity.OrganizationType;
import com.mozip.domain.organization.repository.MemberRepository;
import com.mozip.domain.organization.repository.OrganizationRepository;
import com.mozip.domain.posting.entity.Posting;
import com.mozip.domain.posting.repository.PostingRepository;
import com.mozip.domain.user.entity.User;
import com.mozip.domain.user.repository.UserRepository;

import jakarta.persistence.EntityManager;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class DocEvaluationRepositoryTest {

	@Autowired
	private DocEvaluationRepository docEvaluationRepository;

	@Autowired
	private ApplicationRepository applicationRepository;

	@Autowired
	private PostingRepository postingRepository;

	@Autowired
	private OrganizationRepository organizationRepository;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private User operator;
	private Member member;
	private Posting posting;
	private Application application;

	@BeforeEach
	void setUp() {
		Organization organization = organizationRepository.save(
			Organization.create("테스트 단체", OrganizationType.CLUB, null, OrganizationScope.CAMPUS, null, true, null,
				null));
		operator = userRepository.save(User.create("01055550001", "encoded", "운영진", false, LocalDateTime.now()));
		member = memberRepository.save(Member.create(organization, operator, null, MemberRole.REPRESENTATIVE));
		User applicant = userRepository.save(User.create("01055550002", "encoded", "지원자", false, LocalDateTime.now()));
		posting = postingRepository.save(Posting.create(organization, "평가 공고"));
		application = applicationRepository.save(Application.create(posting, null, applicant));
	}

	@Test
	@DisplayName("문항별 점수는 jsonb 배열로 저장되고 다시 읽으면 같은 값의 DocScore 목록이 된다")
	void scoresRoundTripThroughJsonb() {
		DocEvaluation evaluation = DocEvaluation.create(application, member);
		evaluation.submit(Opinion.PASS, List.of(new DocScore(11L, 5), new DocScore(12L, 1)), "메모", LocalDateTime.now());
		Long id = docEvaluationRepository.saveAndFlush(evaluation).getId();
		entityManager.clear();

		String stored = jdbcTemplate.queryForObject("select scores::text from doc_evaluations where id = ?",
			String.class, id);
		assertThat(stored).isEqualTo("[{\"score\": 5, \"questionId\": 11}, {\"score\": 1, \"questionId\": 12}]");

		DocEvaluation found = docEvaluationRepository.findById(id).orElseThrow();
		assertThat(found.getScores()).containsExactly(new DocScore(11L, 5), new DocScore(12L, 1));
		assertThat(found.getStatus()).isEqualTo(DocEvaluationStatus.SAVED);
		assertThat(found.getOpinion()).isEqualTo(Opinion.PASS);
	}

	@Test
	@DisplayName("점수 없이 만든 평가는 빈 배열로 저장되고, 점수를 바꿔 저장하면 jsonb도 바뀐다")
	void emptyScoresThenOverwrite() {
		Long id = docEvaluationRepository.saveAndFlush(DocEvaluation.create(application, member)).getId();
		entityManager.clear();

		DocEvaluation found = docEvaluationRepository.findById(id).orElseThrow();
		assertThat(found.getScores()).isEmpty();

		found.saveDraft(null, List.of(new DocScore(11L, 3)), null);
		docEvaluationRepository.flush();
		entityManager.clear();

		assertThat(docEvaluationRepository.findById(id).orElseThrow().getScores())
			.containsExactly(new DocScore(11L, 3));
	}

	@Test
	@DisplayName("지원서와 회원, 공고와 회원으로 내 평가를 찾고 다른 회원의 평가는 나오지 않는다")
	void findsMyEvaluations() {
		User other = userRepository.save(User.create("01055550003", "encoded", "다른 운영진", false, LocalDateTime.now()));
		Member otherMember = memberRepository.save(
			Member.create(member.getOrganization(), other, null, MemberRole.OPERATOR));
		DocEvaluation mine = docEvaluationRepository.save(DocEvaluation.create(application, member));
		docEvaluationRepository.save(DocEvaluation.create(application, otherMember));
		docEvaluationRepository.flush();
		entityManager.clear();

		assertThat(docEvaluationRepository.findByApplicationIdAndMemberId(application.getId(), member.getId()))
			.get().extracting(DocEvaluation::getId).isEqualTo(mine.getId());
		assertThat(docEvaluationRepository.findByApplicationIdAndUserId(application.getId(), operator.getId()))
			.get().extracting(DocEvaluation::getId).isEqualTo(mine.getId());
		assertThat(docEvaluationRepository.findAllByPostingIdAndUserId(posting.getId(), operator.getId()))
			.extracting(DocEvaluation::getId).containsExactly(mine.getId());
	}

}
