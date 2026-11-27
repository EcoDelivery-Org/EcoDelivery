package pt.ua.tqs.ecodelivery.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Body of a request to register or update a courier; the company is optional but needed to use the fleet (BR7). */
public record CourierRequest(
        @NotBlank(message = "Courier name is required")
        String name,

        @NotBlank(message = "Email is required")
        @Email(message = "Email is not valid")
        String email,

        String phone,

        Long companyId) {
}
