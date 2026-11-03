package com.example.requests.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface StateChangeJpaRepository extends JpaRepository<StateChangeEntity, Long> {

	List<StateChangeEntity> findByRequestIdOrderByIdAsc(Long requestId);
}
