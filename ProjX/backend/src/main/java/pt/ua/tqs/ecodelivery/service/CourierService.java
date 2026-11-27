package pt.ua.tqs.ecodelivery.service;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.ua.tqs.ecodelivery.dto.CourierRequest;
import pt.ua.tqs.ecodelivery.exception.BusinessRuleException;
import pt.ua.tqs.ecodelivery.exception.ResourceNotFoundException;
import pt.ua.tqs.ecodelivery.model.Courier;
import pt.ua.tqs.ecodelivery.repository.CourierRepository;

/** Registration and maintenance of couriers. */
@Service
public class CourierService {

    private final CourierRepository courierRepository;
    private final CompanyService companyService;

    public CourierService(CourierRepository courierRepository, CompanyService companyService) {
        this.courierRepository = courierRepository;
        this.companyService = companyService;
    }

    @Transactional
    public Courier create(CourierRequest request) {
        if (courierRepository.existsByEmailIgnoreCase(request.email().trim())) {
            throw new BusinessRuleException("A courier with email '" + request.email().trim() + "' already exists");
        }
        Courier courier = new Courier();
        apply(courier, request);
        return courierRepository.save(courier);
    }

    public List<Courier> findAll(Long companyId) {
        return companyId == null ? courierRepository.findAll() : courierRepository.findByCompanyId(companyId);
    }

    public Courier get(Long id) {
        return courierRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Courier " + id + " not found"));
    }

    @Transactional
    public Courier update(Long id, CourierRequest request) {
        Courier courier = get(id);
        if (!request.email().trim().equalsIgnoreCase(courier.getEmail())
                && courierRepository.existsByEmailIgnoreCase(request.email().trim())) {
            throw new BusinessRuleException("A courier with email '" + request.email().trim() + "' already exists");
        }
        apply(courier, request);
        return courierRepository.save(courier);
    }

    private void apply(Courier courier, CourierRequest request) {
        courier.setName(request.name().trim());
        courier.setEmail(request.email().trim());
        courier.setPhone(request.phone());
        courier.setCompany(request.companyId() != null ? companyService.get(request.companyId()) : null);
    }
}
