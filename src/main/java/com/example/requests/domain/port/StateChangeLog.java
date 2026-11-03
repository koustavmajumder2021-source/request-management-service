package com.example.requests.domain.port;

import com.example.requests.domain.StateChange;

import java.util.List;

/** Append-only audit log. */
public interface StateChangeLog {

	void append(StateChange change);

	/** Oldest first. */
	List<StateChange> findByRequestId(long requestId);
}
