package com.mozip.domain.user.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mozip.domain.user.entity.User;
import com.mozip.domain.user.repository.UserRepository;
import com.mozip.global.exception.BusinessException;
import com.mozip.global.exception.CommonErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

	private final UserRepository userRepository;

	// 회원이 없으면 404
	public User getMe(Long userId) {
		return userRepository.findWithLastOrganizationById(userId)
			.orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND).addContext("userId", userId));
	}

}
