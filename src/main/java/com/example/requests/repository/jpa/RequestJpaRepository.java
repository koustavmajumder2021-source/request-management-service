package com.example.requests.repository.jpa;

import com.example.requests.model.RequestState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

interface RequestJpaRepository extends JpaRepository<RequestEntity, Long>, JpaSpecificationExecutor<RequestEntity> {

	Optional<RequestEntity> findFirstByNameOrderByIdDesc(String name);

	boolean existsByNameAndStateNot(String name, RequestState state);
}
