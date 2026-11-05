package com.example.requests.dto;

import com.example.requests.model.Request;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateContentBody(@NotBlank @Size(max = Request.MAX_CONTENT_LENGTH) String content) {
}
