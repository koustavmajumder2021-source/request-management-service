package com.example.requests.repository.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

interface RequestJpaRepository extends JpaRepository<RequestEntity, UUID>, JpaSpecificationExecutor<RequestEntity> {
}
