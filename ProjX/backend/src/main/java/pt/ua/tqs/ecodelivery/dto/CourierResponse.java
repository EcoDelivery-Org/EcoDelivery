package pt.ua.tqs.ecodelivery.dto;

/** A courier as returned by the API. */
public record CourierResponse(Long id, String name, String email, String phone, Long companyId, boolean eligible) {
}
