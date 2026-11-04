package com.example.requests.repository.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface RequestJpaRepository extends JpaRepository<RequestEntity, Long>, JpaSpecificationExecutor<RequestEntity> {
}
