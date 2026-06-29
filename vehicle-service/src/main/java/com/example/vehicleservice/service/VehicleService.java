package com.example.vehicleservice.service;

import com.example.vehicleservice.domain.FuelType;
import com.example.vehicleservice.domain.Vehicle;
import com.example.vehicleservice.domain.VehicleStatus;
import com.example.vehicleservice.exception.DuplicateRegistrationException;
import com.example.vehicleservice.exception.VehicleNotFoundException;
import com.example.vehicleservice.mapper.VehicleMapper;
import com.example.vehicleservice.repository.VehicleRepository;
import com.example.vehicleservice.web.dto.CreateVehicleRequest;
import com.example.vehicleservice.web.dto.UpdateVehicleRequest;
import com.example.vehicleservice.web.dto.VehicleResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Logique métier du parc automobile. Service mono-base (pas de synchronisation externe),
 * donc transactions JPA simples sans compensation.
 */
@Service
public class VehicleService {

    private static final Logger log = LoggerFactory.getLogger(VehicleService.class);

    private final VehicleRepository vehicleRepository;
    private final VehicleMapper vehicleMapper;

    public VehicleService(VehicleRepository vehicleRepository, VehicleMapper vehicleMapper) {
        this.vehicleRepository = vehicleRepository;
        this.vehicleMapper = vehicleMapper;
    }

    @Transactional
    public VehicleResponse create(CreateVehicleRequest request) {
        if (vehicleRepository.existsByRegistrationNumber(request.registrationNumber())) {
            throw new DuplicateRegistrationException(request.registrationNumber());
        }
        Vehicle vehicle = new Vehicle();
        vehicle.setBrand(request.brand());
        vehicle.setModel(request.model());
        vehicle.setRegistrationNumber(request.registrationNumber());
        vehicle.setGearboxType(request.gearboxType());
        vehicle.setFuelType(request.fuelType());
        vehicle.setStatus(VehicleStatus.AVAILABLE);
        vehicle.setManufactureYear(request.manufactureYear());
        vehicle.setMileage(request.mileage());
        vehicle.setInsuranceExpiry(request.insuranceExpiry());
        vehicle.setTechnicalInspectionExpiry(request.technicalInspectionExpiry());

        Vehicle saved = vehicleRepository.save(vehicle);
        log.info("Véhicule créé id={} immatriculation={}", saved.getId(), saved.getRegistrationNumber());
        return vehicleMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public VehicleResponse get(UUID id) {
        return vehicleMapper.toResponse(findOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<VehicleResponse> list(VehicleStatus status, FuelType fuelType) {
        List<Vehicle> vehicles;
        if (status != null) {
            vehicles = vehicleRepository.findByStatus(status);
        } else if (fuelType != null) {
            vehicles = vehicleRepository.findByFuelType(fuelType);
        } else {
            vehicles = vehicleRepository.findAll();
        }
        return vehicles.stream().map(vehicleMapper::toResponse).toList();
    }

    @Transactional
    public VehicleResponse update(UUID id, UpdateVehicleRequest request) {
        Vehicle vehicle = findOrThrow(id);
        if (request.brand() != null) {
            vehicle.setBrand(request.brand());
        }
        if (request.model() != null) {
            vehicle.setModel(request.model());
        }
        if (request.gearboxType() != null) {
            vehicle.setGearboxType(request.gearboxType());
        }
        if (request.fuelType() != null) {
            vehicle.setFuelType(request.fuelType());
        }
        if (request.manufactureYear() != null) {
            vehicle.setManufactureYear(request.manufactureYear());
        }
        if (request.mileage() != null) {
            vehicle.setMileage(request.mileage());
        }
        if (request.insuranceExpiry() != null) {
            vehicle.setInsuranceExpiry(request.insuranceExpiry());
        }
        if (request.technicalInspectionExpiry() != null) {
            vehicle.setTechnicalInspectionExpiry(request.technicalInspectionExpiry());
        }
        // saveAndFlush : updatedAt rafraîchi dans la réponse
        return vehicleMapper.toResponse(vehicleRepository.saveAndFlush(vehicle));
    }

    @Transactional
    public VehicleResponse updateStatus(UUID id, VehicleStatus status) {
        Vehicle vehicle = findOrThrow(id);
        vehicle.setStatus(status);
        return vehicleMapper.toResponse(vehicleRepository.saveAndFlush(vehicle));
    }

    /** Retire un véhicule du service (statut OUT_OF_SERVICE) ; conserve l'historique d'entretien. */
    @Transactional
    public void retire(UUID id) {
        Vehicle vehicle = findOrThrow(id);
        vehicle.setStatus(VehicleStatus.OUT_OF_SERVICE);
        vehicleRepository.save(vehicle);
        log.info("Véhicule retiré (OUT_OF_SERVICE) id={}", id);
    }

    private Vehicle findOrThrow(UUID id) {
        return vehicleRepository.findById(id).orElseThrow(() -> new VehicleNotFoundException(id));
    }
}