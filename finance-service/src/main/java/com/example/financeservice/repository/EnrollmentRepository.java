package com.example.financeservice.repository;

import com.example.financeservice.domain.Enrollment;
import com.example.financeservice.domain.EnrollmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EnrollmentRepository extends JpaRepository<Enrollment, UUID> {

    List<Enrollment> findByClientIdOrderByEnrolledAtDesc(String clientId);

    Optional<Enrollment> findFirstByClientIdAndStatusOrderByEnrolledAtAsc(String clientId, EnrollmentStatus status);
}
