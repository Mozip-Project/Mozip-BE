package com.mozip.domain.interview.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mozip.domain.interview.entity.Interview;
import com.mozip.domain.interview.entity.Interviewer;
import com.mozip.domain.posting.entity.Posting;

public interface InterviewerRepository extends JpaRepository<Interviewer, Long> {

	@Query("select ir from Interviewer ir join fetch ir.member m join fetch m.user u left join fetch u.school "
		+ "join fetch m.organization o left join fetch o.school left join fetch m.team t "
		+ "left join fetch t.organization where ir.interview in :interviews order by ir.id")
	List<Interviewer> findAllWithMemberByInterviewIn(@Param("interviews") Collection<Interview> interviews);

	// 이 칸 말고 시간이 겹치는 다른 면접 칸에 들어가 있는 면접관. 다른 공고의 면접도 포함
	@Query("select ir from Interviewer ir join fetch ir.interview i join fetch i.posting "
		+ "where ir.member.id in :memberIds and i.id <> :interviewId "
		+ "and i.startAt < :endAt and i.endAt > :startAt order by i.startAt, i.track, ir.id")
	List<Interviewer> findAllOverlapping(@Param("memberIds") Collection<Long> memberIds,
		@Param("interviewId") Long interviewId, @Param("startAt") LocalDateTime startAt,
		@Param("endAt") LocalDateTime endAt);

	@Modifying(flushAutomatically = true)
	@Query("delete from Interviewer ir where ir.interview.id in :interviewIds")
	void deleteAllByInterviewIdIn(@Param("interviewIds") Collection<Long> interviewIds);

	@Modifying(flushAutomatically = true)
	@Query("delete from Interviewer ir where ir.interview = :interview")
	void deleteAllByInterview(@Param("interview") Interview interview);

	@Modifying(flushAutomatically = true)
	@Query("delete from Interviewer ir "
		+ "where ir.interview.id in (select i.id from Interview i where i.posting = :posting)")
	void deleteAllByPosting(@Param("posting") Posting posting);

}
