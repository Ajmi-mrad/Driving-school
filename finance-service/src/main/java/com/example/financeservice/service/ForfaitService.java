package com.example.financeservice.service;

import com.example.financeservice.domain.Forfait;
import com.example.financeservice.exception.ForfaitNotFoundException;
import com.example.financeservice.mapper.FinanceMapper;
import com.example.financeservice.repository.ForfaitRepository;
import com.example.financeservice.web.dto.CreateForfaitRequest;
import com.example.financeservice.web.dto.ForfaitResponse;
import com.example.financeservice.web.dto.UpdateForfaitRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Catalogue des forfaits : définition (heures de conduite, séances de code, prix) et gestion
 * d'activation. Écriture réservée au staff ; lecture ouverte à tout utilisateur authentifié.
 */
@Service
public class ForfaitService {

    private final ForfaitRepository forfaitRepository;
    private final FinanceMapper mapper;

    public ForfaitService(ForfaitRepository forfaitRepository, FinanceMapper mapper) {
        this.forfaitRepository = forfaitRepository;
        this.mapper = mapper;
    }

    @Transactional
    public ForfaitResponse create(CreateForfaitRequest req) {
        Forfait forfait = new Forfait();
        forfait.setName(req.name());
        forfait.setDescription(req.description());
        forfait.setDrivingHours(req.drivingHours());
        forfait.setCodeSessions(req.codeSessions());
        forfait.setPrice(req.price());
        forfait.setActive(true);
        return mapper.toForfaitResponse(forfaitRepository.save(forfait));
    }

    @Transactional
    public ForfaitResponse update(UUID id, UpdateForfaitRequest req) {
        Forfait forfait = forfaitRepository.findById(id).orElseThrow(() -> new ForfaitNotFoundException(id));
        forfait.setName(req.name());
        forfait.setDescription(req.description());
        forfait.setDrivingHours(req.drivingHours());
        forfait.setCodeSessions(req.codeSessions());
        forfait.setPrice(req.price());
        forfait.setActive(req.active());
        return mapper.toForfaitResponse(forfait);
    }

    @Transactional(readOnly = true)
    public ForfaitResponse get(UUID id) {
        return forfaitRepository.findById(id)
                .map(mapper::toForfaitResponse)
                .orElseThrow(() -> new ForfaitNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public List<ForfaitResponse> list(boolean activeOnly) {
        List<Forfait> forfaits = activeOnly ? forfaitRepository.findByActiveTrue() : forfaitRepository.findAll();
        return forfaits.stream().map(mapper::toForfaitResponse).toList();
    }
}
