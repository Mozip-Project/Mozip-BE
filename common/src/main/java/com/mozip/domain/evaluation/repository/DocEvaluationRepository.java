package com.mozip.domain.evaluation.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mozip.domain.application.entity.Application;
import com.mozip.domain.evaluation.entity.DocEvaluation;
import com.mozip.domain.evaluation.entity.DocEvaluationStatus;

public interface DocEvaluationRepository extends JpaRepository<DocEvaluation, Long> {

	Optional<DocEvaluation> findByApplicationIdAndMemberId(Long applicationId, Long memberId);

	// 한 지원서에 이 회원이 쓴 평가. 회원은 단체마다 Member가 하나라 많아야 한 건
	@Query("select e from DocEvaluation e where e.application.id = :applicationId and e.member.user.id = :userId")
	Optional<DocEvaluation> findByApplicationIdAndUserId(@Param("applicationId") Long applicationId,
		@Param("userId") Long userId);

	// 한 공고에서 이 회원이 쓴 평가 전부. 목록의 내 평가 상태를 지원서마다 따로 조회하지 않으려고 한 번에 가져옴
	@Query("select e from DocEvaluation e where e.application.posting.id = :postingId and e.member.user.id = :userId")
	List<DocEvaluation> findAllByPostingIdAndUserId(@Param("postingId") Long postingId, @Param("userId") Long userId);

	// 서류 평가 취합 (#91). 지원서 여러 건의 평가를 상태로 걸러 한 번에 조회
	List<DocEvaluation> findAllByApplicationInAndStatus(Collection<Application> applications,
		DocEvaluationStatus status);

	// 지원서 한 건의 운영진별 평가 (#91). GraphQL 하위 필드가 읽는 지원서, 운영진, 회원, 단체, 팀을 함께 조회
	@Query("select e from DocEvaluation e join fetch e.application join fetch e.member m join fetch m.user u "
		+ "left join fetch u.school join fetch m.organization o left join fetch o.school "
		+ "left join fetch m.team t left join fetch t.organization "
		+ "where e.application = :application and e.status = :status order by m.id")
	List<DocEvaluation> findAllWithMemberByApplicationAndStatus(@Param("application") Application application,
		@Param("status") DocEvaluationStatus status);

}
