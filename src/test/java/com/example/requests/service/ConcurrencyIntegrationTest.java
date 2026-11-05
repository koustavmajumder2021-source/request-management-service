package com.example.requests.service;

import com.example.requests.exception.InvalidStateTransitionException;
import com.example.requests.model.RequestAction;
import com.example.requests.model.RequestState;
import com.example.requests.model.StateChange;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.ConcurrencyFailureException;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** Not transactional on purpose: each service call commits on its own thread. */
@SpringBootTest
class ConcurrencyIntegrationTest {

	private static final int THREADS = 8;

	@Autowired
	private RequestService service;

	@Test
	void concurrentPublicationsOfDifferentRequestsGetUniqueNumbers() throws Exception {
		List<UUID> ids = new ArrayList<>();
		for (int i = 0; i < 20; i++) {
			ids.add(accepted("Concurrent " + i));
		}
		Set<Long> numbers = ConcurrentHashMap.newKeySet();

		runConcurrently(ids.stream().<Callable<Void>>map(id -> () -> {
			numbers.add(service.transition(id, RequestAction.PUBLISH, null).publicationNumber());
			return null;
		}).toList());

		assertThat(numbers).hasSize(ids.size());
	}

	@Test
	void concurrentTransitionsOfTheSameRequestHaveExactlyOneWinner() throws Exception {
		UUID id = accepted("Contended");
		AtomicInteger successes = new AtomicInteger();
		AtomicInteger expectedFailures = new AtomicInteger();
		List<Callable<Void>> tasks = new ArrayList<>();
		for (int i = 0; i < THREADS; i++) {
			RequestAction action = i % 2 == 0 ? RequestAction.PUBLISH : RequestAction.REJECT;
			tasks.add(() -> {
				try {
					service.transition(id, action, "racing");
					successes.incrementAndGet();
				}
				catch (InvalidStateTransitionException | ConcurrencyFailureException ex) {
					expectedFailures.incrementAndGet();
				}
				return null;
			});
		}

		runConcurrently(tasks);

		assertThat(successes).hasValue(1);
		assertThat(expectedFailures).hasValue(THREADS - 1);
		assertThat(service.auditLog(id)).hasSize(4).last().extracting(StateChange::action)
				.isIn(RequestAction.PUBLISH, RequestAction.REJECT);
		assertThat(service.auditLog(id).get(3).toState()).isIn(RequestState.PUBLISHED, RequestState.REJECTED);
	}

	@Test
	void concurrentCreationsWithTheSameNameAllSucceedWithDistinctIds() throws Exception {
		Set<UUID> ids = ConcurrentHashMap.newKeySet();
		List<Callable<Void>> tasks = new ArrayList<>();
		for (int i = 0; i < THREADS; i++) {
			tasks.add(() -> {
				ids.add(service.create("Same name", "Body").id());
				return null;
			});
		}

		runConcurrently(tasks);

		assertThat(ids).hasSize(THREADS);
	}

	private UUID accepted(String name) {
		UUID id = service.create(name, "Body").id();
		service.transition(id, RequestAction.VERIFY, null);
		service.transition(id, RequestAction.ACCEPT, null);
		return id;
	}

	private static void runConcurrently(List<Callable<Void>> tasks) throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(THREADS);
		CountDownLatch start = new CountDownLatch(1);
		try {
			List<Future<Void>> futures = new ArrayList<>();
			for (Callable<Void> task : tasks) {
				futures.add(executor.submit(() -> {
					start.await();
					return task.call();
				}));
			}
			start.countDown();
			for (Future<Void> future : futures) {
				future.get(30, TimeUnit.SECONDS);
			}
		}
		finally {
			executor.shutdownNow();
		}
	}
}
