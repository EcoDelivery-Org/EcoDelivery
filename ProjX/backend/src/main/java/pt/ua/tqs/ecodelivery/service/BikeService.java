package pt.ua.tqs.ecodelivery.service;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import pt.ua.tqs.ecodelivery.dto.BikeRequest;
import pt.ua.tqs.ecodelivery.model.Bike;
import pt.ua.tqs.ecodelivery.model.BikeEventType;
import pt.ua.tqs.ecodelivery.model.BikeStatus;
import pt.ua.tqs.ecodelivery.policy.PolicyService;
import pt.ua.tqs.ecodelivery.repository.BikeEventRepository;
import pt.ua.tqs.ecodelivery.repository.BikeRepository;
import pt.ua.tqs.ecodelivery.repository.HubRepository;

/** Business logic for the cargo e-bikes and their lifecycle. */
@Service
public class BikeService {

    private final BikeRepository bikeRepository;
    private final HubRepository hubRepository;
    private final BikeEventRepository bikeEventRepository;
    private final PolicyService policyService;

    public BikeService(BikeRepository bikeRepository, HubRepository hubRepository,
                       BikeEventRepository bikeEventRepository, PolicyService policyService) {
        this.bikeRepository = bikeRepository;
        this.hubRepository = hubRepository;
        this.bikeEventRepository = bikeEventRepository;
        this.policyService = policyService;
    }

    /** Registers a new bike at a hub. */
    public Bike registerBike(BikeRequest request) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** Lists bikes, optionally filtered by hub and/or status. */
    public List<Bike> findBikes(Long hubId, BikeStatus status) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public Bike getBike(Long id) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** Changes the lifecycle status of a bike; only valid transitions are allowed. */
    public Bike changeStatus(Long id, BikeStatus newStatus) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** Moves a bike to another hub, if the destination hub has capacity. */
    public Bike moveToHub(Long bikeId, Long hubId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** Activates a bike, making it part of the operating fleet. */
    public Bike activate(Long bikeId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** Deactivates a bike, taking it out of the operating fleet. */
    public Bike deactivate(Long bikeId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** Whether a bike can be reserved now (available, active, enough battery). */
    public boolean isAvailableForReservation(Long bikeId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** Number of bikes per status, for the operator dashboard. */
    public Map<BikeStatus, Long> countByStatus() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** Records an entry in the bike's history. */
    private void recordEvent(Bike bike, BikeEventType type, String details) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
