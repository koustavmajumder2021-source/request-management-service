package com.example.requests.repository.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface StateChangeJpaRepository extends JpaRepository<StateChangeEntity, Long> {

	List<StateChangeEntity> findByRequestIdOrderByIdAsc(UUID requestId);
}
