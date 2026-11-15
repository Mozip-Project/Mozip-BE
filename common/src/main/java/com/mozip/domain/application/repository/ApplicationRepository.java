package com.mozip.domain.application.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mozip.domain.application.entity.Application;

public interface ApplicationRepository extends JpaRepository<Application, Long> {

}
