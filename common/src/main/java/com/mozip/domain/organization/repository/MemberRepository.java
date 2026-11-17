package com.mozip.domain.organization.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mozip.domain.organization.entity.Member;

public interface MemberRepository extends JpaRepository<Member, Long> {

}
