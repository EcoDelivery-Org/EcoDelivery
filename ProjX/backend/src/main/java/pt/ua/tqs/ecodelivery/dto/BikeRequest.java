package pt.ua.tqs.ecodelivery.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import pt.ua.tqs.ecodelivery.model.BikeStatus;

/** Body of a request to register a bike. */
public record BikeRequest(
        @NotBlank(message = "Serial number is required")
        String serialNumber,

        @NotBlank(message = "Model is required")
        String model,

        @Min(value = 1, message = "Cargo capacity must be at least 1 kg")
        double cargoCapacityKg,

        Long hubId,

        Integer batteryLevel,

        BikeStatus status) {

    public BikeRequest(String serialNumber, String model, double cargoCapacityKg, Long hubId) {
        this(serialNumber, model, cargoCapacityKg, hubId, 100, BikeStatus.AVAILABLE);
    }
}
