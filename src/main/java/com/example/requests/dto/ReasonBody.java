package com.example.requests.dto;

import com.example.requests.model.Request;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReasonBody(@NotBlank @Size(max = Request.MAX_REASON_LENGTH) String reason) {
}
