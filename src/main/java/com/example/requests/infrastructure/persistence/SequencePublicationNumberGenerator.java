package com.example.requests.infrastructure.persistence;

import com.example.requests.domain.port.PublicationNumberGenerator;
import jakarta.persistence.EntityManager;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.springframework.stereotype.Component;

/**
 * Backed by a database sequence: unique across transactions and instances, not gap-free.
 * The next-value SQL comes from the Hibernate dialect to stay database agnostic.
 */
@Component
class SequencePublicationNumberGenerator implements PublicationNumberGenerator {

	static final String SEQUENCE_NAME = "publication_number_seq";

	private final EntityManager entityManager;
	private final String nextValueSql;

	SequencePublicationNumberGenerator(EntityManager entityManager) {
		this.entityManager = entityManager;
		this.nextValueSql = entityManager.getEntityManagerFactory()
				.unwrap(SessionFactoryImplementor.class)
				.getJdbcServices()
				.getDialect()
				.getSequenceSupport()
				.getSequenceNextValString(SEQUENCE_NAME);
	}

	/**
	 * @return the next value of {@code publication_number_seq}; a value drawn in a transaction that later
	 *         rolls back is not reused
	 */
	@Override
	public long next() {
		return ((Number) entityManager.createNativeQuery(nextValueSql).getSingleResult()).longValue();
	}
}
