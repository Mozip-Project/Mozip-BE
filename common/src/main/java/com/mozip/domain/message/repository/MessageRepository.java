package com.mozip.domain.message.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mozip.domain.message.entity.Message;
import com.mozip.domain.message.entity.MessageTarget;
import com.mozip.domain.message.entity.MessageType;
import com.mozip.domain.posting.entity.Posting;
import com.mozip.domain.user.entity.User;

import jakarta.persistence.LockModeType;

public interface MessageRepository extends JpaRepository<Message, Long> {

	List<Message> findAllByPostingAndType(Posting posting, MessageType type);

	// 초안 저장용. 행을 잠가 두어 저장 도중 발송이 시작돼도 SENDING이 DRAFT로 덮어써지지 않게 함
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
		SELECT m FROM Message m
		WHERE m.posting = :posting AND m.type = :type AND m.target = :target
		""")
	Optional<Message> findForUpdate(@Param("posting") Posting posting, @Param("type") MessageType type,
		@Param("target") MessageTarget target);

	// 발송 시작. 조건부 UPDATE라 동시에 두 번 눌러도 한 요청만 1을 받고 나머지는 0을 받음
	@Modifying(flushAutomatically = true)
	@Query("""
		UPDATE Message m
		SET m.status = com.mozip.domain.message.entity.MessageStatus.SENDING, m.sender = :sender, m.updatedAt = :now
		WHERE m.id = :id AND m.status = com.mozip.domain.message.entity.MessageStatus.DRAFT
		""")
	int startSending(@Param("id") Long id, @Param("sender") User sender, @Param("now") LocalDateTime now);

}
