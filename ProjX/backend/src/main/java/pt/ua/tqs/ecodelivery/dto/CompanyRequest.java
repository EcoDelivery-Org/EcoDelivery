package pt.ua.tqs.ecodelivery.dto;

import jakarta.validation.constraints.NotBlank;

/** Body of a request to register a company. */
public record CompanyRequest(@NotBlank(message = "Company name is required") String name) {
}
