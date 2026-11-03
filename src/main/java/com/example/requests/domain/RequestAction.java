package com.example.requests.domain;

/**
 * Lifecycle actions. {@code CREATE} only appears in the audit log; it is not a transition.
 */
public enum RequestAction {
	CREATE(false),
	VERIFY(false),
	ACCEPT(false),
	PUBLISH(false),
	REJECT(true),
	DELETE(true);

	private final boolean reasonRequired;

	RequestAction(boolean reasonRequired) {
		this.reasonRequired = reasonRequired;
	}

	public boolean isReasonRequired() {
		return reasonRequired;
	}
}
