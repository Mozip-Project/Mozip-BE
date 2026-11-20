package com.mozip.domain.organization.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import com.mozip.TestcontainersConfiguration;
import com.mozip.devtools.seed.SeedFixtures;
import com.mozip.domain.organization.dto.CreateOrganizationInput;
import com.mozip.domain.organization.dto.TeamInput;
import com.mozip.domain.organization.entity.Invitation;
import com.mozip.domain.organization.entity.Member;
import com.mozip.domain.organization.entity.MemberRole;
import com.mozip.domain.organization.entity.Organization;
import com.mozip.domain.organization.entity.OrganizationScope;
import com.mozip.domain.organization.entity.OrganizationType;
import com.mozip.domain.organization.entity.Team;
import com.mozip.domain.organization.exception.MemberInputReason;
import com.mozip.domain.organization.exception.TeamErrorCode;
import com.mozip.domain.organization.repository.MemberRepository;
import com.mozip.domain.organization.repository.TeamRepository;
import com.mozip.domain.posting.entity.Position;
import com.mozip.domain.posting.entity.Posting;
import com.mozip.domain.posting.repository.PositionRepository;
import com.mozip.domain.posting.repository.PostingRepository;
import com.mozip.domain.user.entity.User;
import com.mozip.domain.user.repository.UserRepository;
import com.mozip.global.exception.BusinessException;
import com.mozip.global.exception.CommonErrorCode;

// 테스트 메서드마다 트랜잭션을 열고 끝나면 롤백해 다른 테스트에 데이터가 남지 않음
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class TeamServiceTest {

	@Autowired
	private TeamService teamService;

	@Autowired
	private InvitationService invitationService;

	@Autowired
	private OrganizationService organizationService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private TeamRepository teamRepository;

	@Autowired
	private PostingRepository postingRepository;

	@Autowired
	private PositionRepository positionRepository;

	private User representative;
	private Organization organization;
	private Team planning;
	private Team design;

	@BeforeEach
	void setUp() {
		representative = userRepository.save(
			User.create("01099990041", "encoded", "대표자", false, LocalDateTime.now()));
		organization = organizationService.createOrganization(representative.getId(),
			SeedFixtures.createOrganizationInput());
		planning = teamRepository.save(Team.create(organization, "기획팀", 0));
		design = teamRepository.save(Team.create(organization, "디자인팀", 1));
	}

	@Test
	@DisplayName("보낸 목록으로 전체 교체한다. id 있는 팀은 이름과 순서만 바뀌고, 새 팀은 생기고, 빠진 팀은 지워진다")
	void replaceAll() {
		List<Team> saved = teamService.saveTeams(representative.getId(), organization.getId(), List.of(
			new TeamInput(null, " 개발팀 "),
			new TeamInput(planning.getId(), "기획/운영팀")));
		teamRepository.flush();

		assertThat(saved).extracting(Team::getName, Team::getSortOrder)
			.containsExactly(org.assertj.core.groups.Tuple.tuple("개발팀", 0),
				org.assertj.core.groups.Tuple.tuple("기획/운영팀", 1));
		assertThat(saved.get(1).getId()).isEqualTo(planning.getId());
		assertThat(teamRepository.findAllByOrganizationIdOrderBySortOrderAscIdAsc(organization.getId()))
			.extracting(Team::getName)
			.containsExactly("개발팀", "기획/운영팀");
	}

	@Test
	@DisplayName("삭제되는 팀의 운영진과 대기 초대는 팀 미지정으로 바뀐다")
	void deletedTeamMembersBecomeUnassigned() {
		User operator = userRepository.save(User.create("01099990042", "encoded", "운영진", false, LocalDateTime.now()));
		Member member = memberRepository.save(Member.create(organization, operator, design, MemberRole.OPERATOR));
		Invitation invitation = invitationService.inviteMember(representative.getId(), organization.getId(),
			"01099990043", design.getId());

		teamService.saveTeams(representative.getId(), organization.getId(),
			List.of(new TeamInput(planning.getId(), "기획팀")));
		teamRepository.flush();

		assertThat(member.getTeam()).isNull();
		assertThat(invitation.getTeam()).isNull();
		assertThat(teamRepository.findById(design.getId())).isEmpty();
	}

	@Test
	@DisplayName("공고 포지션이 쓰는 팀은 목록에서 빼면 TEAM_409다")
	void teamUsedByPosition() {
		Posting posting = postingRepository.save(Posting.create(organization, "모집 공고", false, false));
		positionRepository.save(Position.create(posting, design, "디자이너", null));

		assertThatThrownBy(() -> teamService.saveTeams(representative.getId(), organization.getId(),
			List.of(new TeamInput(planning.getId(), "기획팀"))))
			.isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.getErrorCode()).isEqualTo(TeamErrorCode.TEAM_IN_USE));
	}

	@Test
	@DisplayName("팀 없는 단체, 다른 단체 팀 id, 빈 이름, 중복 이름은 COMMON_400이고 운영진이 아니면 COMMON_403이다")
	void rejected() {
		Organization noTeam = organizationService.createOrganization(representative.getId(),
			new CreateOrganizationInput("팀 없는 단체", OrganizationType.CLUB, null, OrganizationScope.UNION, null, true,
				null, null));
		Organization other = organizationService.createOrganization(representative.getId(),
			SeedFixtures.createOrganizationInput());
		Team otherTeam = teamRepository.save(Team.create(other, "다른 팀", 0));
		User outsider = userRepository.save(User.create("01099990044", "encoded", "외부인", false, LocalDateTime.now()));

		assertInvalid(noTeam.getId(), List.of(new TeamInput(null, "팀")), MemberInputReason.NO_TEAM_ORGANIZATION);
		assertInvalid(organization.getId(), List.of(new TeamInput(otherTeam.getId(), "팀")),
			MemberInputReason.TEAM_NOT_FOUND);
		assertInvalid(organization.getId(), List.of(new TeamInput(null, "  ")), MemberInputReason.TEAM_NAME_INVALID);
		assertInvalid(organization.getId(), List.of(new TeamInput(null, "팀"), new TeamInput(null, "팀 ")),
			MemberInputReason.TEAM_DUPLICATED);
		assertInvalid(organization.getId(),
			List.of(new TeamInput(planning.getId(), "A"), new TeamInput(planning.getId(), "B")),
			MemberInputReason.TEAM_DUPLICATED);
		assertThatThrownBy(() -> teamService.saveTeams(outsider.getId(), organization.getId(), List.of()))
			.isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.FORBIDDEN));
	}

	private void assertInvalid(Long organizationId, List<TeamInput> inputs, MemberInputReason reason) {
		assertThatThrownBy(() -> teamService.saveTeams(representative.getId(), organizationId, inputs))
			.isInstanceOfSatisfying(BusinessException.class, e -> {
				assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_INPUT);
				assertThat(e.getReason()).isEqualTo(reason.name());
			});
	}

}
