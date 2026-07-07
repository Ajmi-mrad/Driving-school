package com.example.financeservice.repository;

import com.example.financeservice.domain.Forfait;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ForfaitRepository extends JpaRepository<Forfait, UUID> {

    List<Forfait> findByActiveTrue();
}
