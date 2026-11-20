package com.mozip.domain.organization.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mozip.domain.organization.entity.Member;
import com.mozip.domain.organization.entity.RepresentativeTransfer;
import com.mozip.domain.organization.entity.TransferStatus;

public interface RepresentativeTransferRepository extends JpaRepository<RepresentativeTransfer, Long> {

	boolean existsByOrganizationIdAndStatus(Long organizationId, TransferStatus status);

	// 응답의 from/to Member(회원, 팀)와 단체(학교)를 그리기 위해 같이 가져옴 (open-in-view가 꺼져 있음)
	@Query("""
		select t from RepresentativeTransfer t
		join fetch t.organization o left join fetch o.school
		join fetch t.fromMember f join fetch f.user left join fetch f.team
		join fetch t.toMember m join fetch m.user left join fetch m.team
		where t.id = :id""")
	Optional<RepresentativeTransfer> findWithMembersById(@Param("id") Long id);

	// 운영진 내보내기 때 그 운영진이 요청자나 대상으로 걸린 대기 요청
	@Query("select t from RepresentativeTransfer t where t.status = :status and (t.fromMember = :member or t.toMember = :member)")
	List<RepresentativeTransfer> findAllByMemberAndStatus(@Param("member") Member member,
		@Param("status") TransferStatus status);

}
