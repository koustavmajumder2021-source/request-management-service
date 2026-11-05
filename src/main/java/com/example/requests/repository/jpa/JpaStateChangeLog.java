package com.example.requests.repository.jpa;

import com.example.requests.model.StateChange;
import com.example.requests.repository.StateChangeLog;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
class JpaStateChangeLog implements StateChangeLog {

	private final StateChangeJpaRepository jpa;

	JpaStateChangeLog(StateChangeJpaRepository jpa) {
		this.jpa = jpa;
	}

	@Override
	public void append(StateChange change) {
		jpa.save(new StateChangeEntity(change.requestId(), change.action(), change.fromState(), change.toState(),
				change.reason(), change.occurredAt()));
	}

	@Override
	public List<StateChange> findByRequestId(UUID requestId) {
		return jpa.findByRequestIdOrderByIdAsc(requestId).stream()
				.map(e -> new StateChange(e.getRequestId(), e.getAction(), e.getFromState(), e.getToState(),
						e.getReason(), e.getOccurredAt()))
				.toList();
	}
}
