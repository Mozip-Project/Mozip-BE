package com.mozip.domain.organization.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mozip.domain.organization.entity.InviteLink;

public interface InviteLinkRepository extends JpaRepository<InviteLink, Long> {

	@Query("select l from InviteLink l join fetch l.organization o left join fetch o.school where l.token = :token")
	Optional<InviteLink> findWithOrganizationByToken(@Param("token") String token);

	// 아직 유효한 링크 중 가장 늦게 만료되는 것 (있으면 새로 만들지 않고 재사용)
	Optional<InviteLink> findFirstByOrganizationIdAndExpiresAtAfterOrderByExpiresAtDesc(Long organizationId,
		LocalDateTime now);

}
