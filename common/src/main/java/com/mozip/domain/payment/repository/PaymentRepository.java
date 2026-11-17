package com.mozip.domain.payment.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mozip.domain.payment.entity.Payment;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

}
