package com.example.requests.dto;

import com.example.requests.model.Request;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CreateRequestBody {

	@NotBlank
	@Size(max = Request.MAX_NAME_LENGTH)
	private String name;

	@NotBlank
	@Size(max = Request.MAX_CONTENT_LENGTH)
	private String content;

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getContent() {
		return content;
	}

	public void setContent(String content) {
		this.content = content;
	}
}
