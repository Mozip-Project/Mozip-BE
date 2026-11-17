package com.mozip.domain.payment.entity;

import java.time.LocalDateTime;

import com.mozip.domain.organization.entity.Organization;
import com.mozip.domain.user.entity.User;
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

@Getter
@Entity
@Table(name = "payments")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "organization_id", nullable = false)
	private Organization organization;

	// 결제한 대표자
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private PaymentProduct product;

	@Column(nullable = false)
	private int amount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private PaymentStatus status;

	private LocalDateTime paidAt;

	private Payment(Organization organization, User user, PaymentProduct product, int amount, PaymentStatus status,
		LocalDateTime paidAt) {
		this.organization = organization;
		this.user = user;
		this.product = product;
		this.amount = amount;
		this.status = status;
		this.paidAt = paidAt;
	}

	public static Payment create(Organization organization, User user, PaymentProduct product, int amount,
		PaymentStatus status, LocalDateTime paidAt) {
		return new Payment(organization, user, product, amount, status, paidAt);
	}

}
