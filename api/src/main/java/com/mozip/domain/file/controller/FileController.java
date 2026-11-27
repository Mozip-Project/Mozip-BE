package com.mozip.domain.file.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mozip.domain.file.dto.PresignedUrlRequest;
import com.mozip.domain.file.dto.PresignedUrlResponse;
import com.mozip.domain.file.service.FileService;
import com.mozip.global.common.response.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController implements FileControllerApi {

	private final FileService fileService;

	@Override
	@PostMapping("/presigned-url")
	public ApiResponse<PresignedUrlResponse> issuePresignedUrl(@RequestBody @Valid PresignedUrlRequest request) {
		return ApiResponse.ok(fileService.issuePresignedUrl(request));
	}

}
