package com.example.bookingservice.repository;

import com.example.bookingservice.domain.Exam;
import com.example.bookingservice.domain.ExamStatus;
import com.example.bookingservice.domain.ExamType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface ExamRepository
        extends JpaRepository<Exam, UUID>, JpaSpecificationExecutor<Exam> {

    /**
     * Nombre de tentatives réelles de l'élève pour ce type → base du numéro de tentative. Les examens
     * annulés (jamais passés) sont exclus pour ne pas gonfler le compteur.
     */
    long countByClientIdAndTypeAndStatusNot(String clientId, ExamType type, ExamStatus status);

    /** Vrai si l'élève a déjà un examen non résolu (planifié) du même type. */
    boolean existsByClientIdAndTypeAndStatus(String clientId, ExamType type, ExamStatus status);

    // Recherche filtrée : voir ExamSpecifications#filter (findAll(Specification, Sort)).
}
