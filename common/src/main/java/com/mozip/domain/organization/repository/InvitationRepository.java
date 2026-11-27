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

	// 그 단체에서 이 회원에게 걸린 대기 초대. 가입자 초대는 invitee로, 미가입 때 받은 초대는 휴대폰 번호로 찾음
	@Query("select i from Invitation i where i.organization.id = :organizationId "
		+ "and i.status = com.mozip.domain.organization.entity.InvitationStatus.PENDING "
		+ "and (i.invitee.id = :userId or i.inviteeContact = :phone)")
	List<Invitation> findAllPendingByOrganizationIdAndInvitee(@Param("organizationId") Long organizationId,
		@Param("userId") Long userId, @Param("phone") String phone);

	List<Invitation> findAllByTeamIn(Collection<Team> teams);

}
