package com.mozip.domain.organization.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mozip.domain.organization.entity.Invitation;
import com.mozip.domain.organization.entity.InvitationStatus;
import com.mozip.domain.organization.entity.Team;

public interface InvitationRepository extends JpaRepository<Invitation, Long> {

	// 단체, 학교, 팀을 함께 조회
	@Query("select i from Invitation i join fetch i.organization o left join fetch o.school left join fetch i.team where i.code = :code")
	Optional<Invitation> findWithOrganizationAndTeamByCode(@Param("code") String code);

	List<Invitation> findAllByOrganizationIdAndInviteeContactAndStatus(Long organizationId, String inviteeContact,
		InvitationStatus status);

	List<Invitation> findAllByTeamIn(Collection<Team> teams);

}
