package com.mozip.domain.organization.entity;

import java.time.LocalDateTime;

import com.mozip.global.common.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 대표자 인수 요청, 대상이 수락하면 두 사람의 역할이 바뀜
// 단체당 대기 중 요청 1개 제약은 DDL에만 있음
@Getter
@Entity
@Table(name = "representative_transfers")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RepresentativeTransfer extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "organization_id", nullable = false)
	private Organization organization;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "from_member_id", nullable = false)
	private Member fromMember;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "to_member_id", nullable = false)
	private Member toMember;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private TransferStatus status;

	private LocalDateTime respondedAt;

	private RepresentativeTransfer(Organization organization, Member fromMember, Member toMember) {
		this.organization = organization;
		this.fromMember = fromMember;
		this.toMember = toMember;
		this.status = TransferStatus.PENDING;
	}

	// 요청자는 대표자, 대상은 같은 단체의 다른 운영진만
	public static RepresentativeTransfer create(Member fromMember, Member toMember) {
		if (!fromMember.isRepresentative() || toMember.isRepresentative()) {
			throw new IllegalArgumentException("대표자가 운영진에게만 요청할 수 있습니다.");
		}
		if (!fromMember.getOrganization().getId().equals(toMember.getOrganization().getId())) {
			throw new IllegalArgumentException("같은 단체 운영진에게만 요청할 수 있습니다.");
		}
		return new RepresentativeTransfer(fromMember.getOrganization(), fromMember, toMember);
	}

	public boolean isPending() {
		return status == TransferStatus.PENDING;
	}

	// 상태만 바꿈 (역할 교체는 서비스에서)
	public void approve(LocalDateTime now) {
		respond(TransferStatus.APPROVED, now);
	}

	public void reject(LocalDateTime now) {
		respond(TransferStatus.REJECTED, now);
	}

	// 요청자나 대상이 단체를 나가면 호출 (운영진 내보내기)
	public void cancel(LocalDateTime now) {
		respond(TransferStatus.CANCELED, now);
	}

	private void respond(TransferStatus status, LocalDateTime now) {
		if (!isPending()) {
			throw new IllegalStateException("이미 처리된 대표자 인수 요청입니다.");
		}
		this.status = status;
		this.respondedAt = now;
	}

}
