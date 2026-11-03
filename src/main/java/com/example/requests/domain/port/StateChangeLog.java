package com.example.requests.domain.port;

import com.example.requests.domain.StateChange;

import java.util.List;

/** Append-only audit log. */
public interface StateChangeLog {

	/**
	 * Records a state change. Implementations must take part in the caller's transaction so that the
	 * entry is committed or rolled back together with the change itself.
	 */
	void append(StateChange change);

	/**
	 * @return all changes of the request, oldest first; empty if there are none
	 */
	List<StateChange> findByRequestId(long requestId);
}
