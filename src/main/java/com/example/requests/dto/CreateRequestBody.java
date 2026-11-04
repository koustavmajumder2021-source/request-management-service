package com.example.requests.dto;

import com.example.requests.model.Request;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateRequestBody(
		@NotBlank @Size(max = Request.MAX_NAME_LENGTH) String name,
		@NotBlank @Size(max = Request.MAX_CONTENT_LENGTH) String content) {
}
