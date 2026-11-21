package pt.ua.tqs.ecodelivery.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import pt.ua.tqs.ecodelivery.dto.BikeRequest;
import pt.ua.tqs.ecodelivery.exception.BusinessRuleException;
import pt.ua.tqs.ecodelivery.exception.ResourceNotFoundException;
import pt.ua.tqs.ecodelivery.model.Bike;
import pt.ua.tqs.ecodelivery.model.BikeEvent;
import pt.ua.tqs.ecodelivery.model.BikeEventType;
import pt.ua.tqs.ecodelivery.model.BikeStatus;
import pt.ua.tqs.ecodelivery.model.Hub;
import pt.ua.tqs.ecodelivery.policy.PolicyService;
import pt.ua.tqs.ecodelivery.repository.BikeEventRepository;
import pt.ua.tqs.ecodelivery.repository.BikeRepository;
import pt.ua.tqs.ecodelivery.repository.HubRepository;

/** Business logic for cargo e-bikes and vehicle inventory management (US01). */
@Service
public class BikeService {

    private final BikeRepository bikeRepository;
    private final HubRepository hubRepository;
    private final BikeEventRepository bikeEventRepository;
    private final PolicyService policyService;
    private final InventoryLogService inventoryLogService;
    private final Clock clock;

    public BikeService(BikeRepository bikeRepository, HubRepository hubRepository,
                       BikeEventRepository bikeEventRepository, PolicyService policyService,
                       InventoryLogService inventoryLogService, Clock clock) {
        this.bikeRepository = bikeRepository;
        this.hubRepository = hubRepository;
        this.bikeEventRepository = bikeEventRepository;
        this.policyService = policyService;
        this.inventoryLogService = inventoryLogService;
        this.clock = clock;
    }

    /** Registers a new bike at a hub with hardware/serial attributes and validates hub capacity (US01). */
    public Bike registerBike(BikeRequest request) {
        if (request.serialNumber() == null || request.serialNumber().isBlank()) {
            throw new BusinessRuleException("Bike serial number cannot be empty");
        }
        if (bikeRepository.existsBySerialNumber(request.serialNumber().trim())) {
            throw new BusinessRuleException("Bike with serial number '" + request.serialNumber().trim() + "' already exists");
        }
        if (request.cargoCapacityKg() <= 0) {
            throw new BusinessRuleException("Cargo capacity must be greater than zero");
        }

        Hub hub = null;
        if (request.hubId() != null) {
            hub = hubRepository.findById(request.hubId())
                    .orElseThrow(() -> new ResourceNotFoundException("Hub not found with id: " + request.hubId()));
            long currentCount = bikeRepository.countByCurrentHubId(hub.getId());
            if (currentCount >= hub.getCapacity()) {
                throw new BusinessRuleException("Hub '" + hub.getName() + "' is at maximum capacity (" + hub.getCapacity() + ")");
            }
        }

        int battery = request.batteryLevel() != null ? request.batteryLevel() : 100;
        if (battery < 0 || battery > 100) {
            throw new BusinessRuleException("Battery level must be between 0 and 100");
        }

        BikeStatus status = request.status() != null ? request.status() : BikeStatus.AVAILABLE;

        Bike bike = new Bike();
        bike.setSerialNumber(request.serialNumber().trim());
        bike.setModel(request.model() != null ? request.model().trim() : "Standard Cargo E-Bike");
        bike.setCargoCapacityKg(request.cargoCapacityKg());
        bike.setBatteryLevel(battery);
        bike.setStatus(status);
        bike.setActive(true);
        bike.setCurrentHub(hub);

        Bike saved = bikeRepository.save(bike);

        recordEvent(saved, BikeEventType.PROVISIONED,
                "Bike registered with serial " + saved.getSerialNumber() + (hub != null ? " at hub " + hub.getName() : ""));
        inventoryLogService.logBikeProvisioned(saved);

        return saved;
    }

    /** Lists bikes in inventory, optionally filtered by hub and/or status (US01). */
    public List<Bike> findBikes(Long hubId, BikeStatus status) {
        if (hubId != null && status != null) {
            return bikeRepository.findByCurrentHubIdAndStatus(hubId, status);
        } else if (hubId != null) {
            return bikeRepository.findByCurrentHubId(hubId);
        } else if (status != null) {
            return bikeRepository.findByStatus(status);
        } else {
            return bikeRepository.findAll();
        }
    }

    /** Gets bike by ID (US01). */
    public Bike getBike(Long id) {
        return bikeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bike not found with id: " + id));
    }

    /** Changes the lifecycle status of a bike (Reserved for US02). */
    public Bike changeStatus(Long id, BikeStatus newStatus) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** Moves a bike to another hub (Reserved for future relocation stories). */
    public Bike moveToHub(Long bikeId, Long hubId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** Activates a bike, making it part of the operating fleet (Reserved for US02). */
    public Bike activate(Long bikeId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** Deactivates a bike, taking it out of the operating fleet (Reserved for US02). */
    public Bike deactivate(Long bikeId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** Whether a bike can be reserved now (Reserved for US08 / US09). */
    public boolean isAvailableForReservation(Long bikeId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** Number of bikes per status, for the operator dashboard (Reserved for US06). */
    public Map<BikeStatus, Long> countByStatus() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** Records an entry in the bike's history (US01 asset audit). */
    private void recordEvent(Bike bike, BikeEventType type, String details) {
        BikeEvent event = new BikeEvent();
        event.setBike(bike);
        event.setType(type);
        event.setTimestamp(LocalDateTime.now(clock));
        event.setDetails(details);
        bikeEventRepository.save(event);
    }
}
