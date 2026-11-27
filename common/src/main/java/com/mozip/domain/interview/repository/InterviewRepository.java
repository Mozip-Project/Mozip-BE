package com.mozip.domain.interview.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mozip.domain.interview.entity.Interview;
import com.mozip.domain.posting.entity.Posting;

import jakarta.persistence.LockModeType;

public interface InterviewRepository extends JpaRepository<Interview, Long> {

	List<Interview> findAllByPostingOrderByStartAtAscTrackAscIdAsc(Posting posting);

	// 이 모집에서 쓴 장소, 최근에 쓴 순
	@Query("select i.location from Interview i where i.posting = :posting and i.location is not null "
		+ "group by i.location order by max(i.updatedAt) desc")
	List<String> findRecentLocations(@Param("posting") Posting posting);

	// 같은 공고의 타임테이블 생성, 수정, 확정이 겹치지 않게 공고 행을 잠금
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from Posting p where p.id = :postingId")
	Optional<Posting> findPostingForUpdate(@Param("postingId") Long postingId);

	// 지원자가 한 명도 없는 칸. 지원자를 옮기거나 빼고 나면 빈 칸은 지움
	@Query("select i.id from Interview i where i.posting = :posting "
		+ "and not exists (select 1 from InterviewApplicant ia where ia.interview = i)")
	List<Long> findEmptyIdsByPosting(@Param("posting") Posting posting);

	// 지금 쓰이는 면접실 열 중 가장 큰 번호, 칸이 없으면 0
	@Query("select coalesce(max(i.track), 0) from Interview i where i.posting = :posting")
	int findMaxTrack(@Param("posting") Posting posting);

	// 아직 확정 전일 때만 확정 시각을 채움. 0이면 이미 확정된 것 (동시에 두 번 눌러도 한 번만 성공)
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("update Posting p set p.timetableConfirmedAt = :now "
		+ "where p.id = :postingId and p.timetableConfirmedAt is null")
	int confirmTimetable(@Param("postingId") Long postingId, @Param("now") LocalDateTime now);

	// 수정 모드에서 다시 확정. 이미 확정된 공고의 확정 시각을 새로 씀
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("update Posting p set p.timetableConfirmedAt = :now "
		+ "where p.id = :postingId and p.timetableConfirmedAt is not null")
	int reconfirmTimetable(@Param("postingId") Long postingId, @Param("now") LocalDateTime now);

	// 칸 여러 개 삭제. 먼저 지운 배정이 영속성 컨텍스트에 남아 있을 수 있어 같이 비움
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from Interview i where i.id in :interviewIds")
	void deleteAllInBulkByIdIn(@Param("interviewIds") Collection<Long> interviewIds);

	@Modifying(flushAutomatically = true)
	@Query("delete from Interview i where i.posting = :posting")
	void deleteAllByPosting(@Param("posting") Posting posting);

}
