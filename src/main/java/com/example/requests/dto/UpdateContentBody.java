package com.example.requests.dto;

import com.example.requests.model.Request;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class UpdateContentBody {

	@NotBlank
	@Size(max = Request.MAX_CONTENT_LENGTH)
	private String content;

	public String getContent() {
		return content;
	}

	public void setContent(String content) {
		this.content = content;
	}
}
