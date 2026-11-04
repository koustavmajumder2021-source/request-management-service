package com.example.requests.dto;

import com.example.requests.model.Request;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ReasonBody {

	@NotBlank
	@Size(max = Request.MAX_REASON_LENGTH)
	private String reason;

	public String getReason() {
		return reason;
	}

	public void setReason(String reason) {
		this.reason = reason;
	}
}
