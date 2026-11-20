package com.mozip.domain.organization.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.mozip.TestcontainersConfiguration;
import com.mozip.devtools.seed.SeedFixtures;
import com.mozip.domain.organization.entity.Member;
import com.mozip.domain.organization.entity.MemberRole;
import com.mozip.domain.organization.entity.Organization;
import com.mozip.domain.organization.repository.MemberRepository;
import com.mozip.domain.user.entity.User;
import com.mozip.domain.user.repository.UserRepository;
import com.mozip.global.exception.BusinessException;
import com.mozip.global.exception.CommonErrorCode;

// 두 트랜잭션이 실제로 겹쳐야 하는 테스트라 @Transactional 롤백을 쓰지 않고 끝나면 직접 지움
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class RepresentativeTransferConcurrencyTest {

	@Autowired
	private RepresentativeTransferService transferService;

	@Autowired
	private MemberService memberService;

	@Autowired
	private OrganizationService organizationService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private Organization organization;
	private User representative;
	private User operator;

	@AfterEach
	void tearDown() {
		Long organizationId = organization.getId();
		jdbcTemplate.update("delete from representative_transfers where organization_id = ?", organizationId);
		jdbcTemplate.update("delete from members where organization_id = ?", organizationId);
		jdbcTemplate.update("delete from users where id in (?, ?)", representative.getId(), operator.getId());
		jdbcTemplate.update("delete from organizations where id = ?", organizationId);
	}

	@Test
	@DisplayName("인수 수락이 끝나기 전 들어온 내보내기는 기다렸다가 바뀐 역할로 판단해 새 대표자를 내보내지 않는다")
	void removeWaitsForApprove() throws Exception {
		representative = saveUser("대표자");
		operator = saveUser("운영진");
		organization = organizationService.createOrganization(representative.getId(),
			SeedFixtures.createOrganizationInput());
		Member operatorMember = memberRepository.save(Member.create(organization, operator, null, MemberRole.OPERATOR));
		Long transferId = transferService.request(representative.getId(), operatorMember.getId()).getId();

		ExecutorService executor = Executors.newSingleThreadExecutor();
		try {
			// 수락 트랜잭션이 단체 행을 잡은 채로 내보내기를 시작하고, 내보내기가 그 사이 끝나지 않는지 확인
			Future<?> removal = new TransactionTemplate(transactionManager).execute(status -> {
				transferService.respond(operator.getId(), transferId, true);
				Future<?> submitted = executor.submit(
					() -> memberService.removeMember(representative.getId(), operatorMember.getId()));
				assertThatThrownBy(() -> submitted.get(1, TimeUnit.SECONDS)).isInstanceOf(TimeoutException.class);
				return submitted;
			});

			// 수락이 커밋된 뒤 내보내기는 호출자가 더는 대표자가 아니라 COMMON_403
			assertThatThrownBy(() -> removal.get(10, TimeUnit.SECONDS))
				.isInstanceOf(ExecutionException.class)
				.cause()
				.isInstanceOfSatisfying(BusinessException.class,
					e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.FORBIDDEN));
		} finally {
			executor.shutdownNow();
		}

		Member promoted = memberRepository.findById(operatorMember.getId()).orElseThrow();
		assertThat(promoted.getRole()).isEqualTo(MemberRole.REPRESENTATIVE);
		assertThat(promoted.getLeftAt()).isNull();
	}

	private User saveUser(String name) {
		String phone = "0107" + String.format("%07d", ThreadLocalRandom.current().nextInt(10_000_000));
		return userRepository.save(User.create(phone, "encoded", name, false, LocalDateTime.now()));
	}

}
