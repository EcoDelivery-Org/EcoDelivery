package pt.ua.tqs.ecodelivery.service;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.ua.tqs.ecodelivery.dto.CompanyRequest;
import pt.ua.tqs.ecodelivery.exception.BusinessRuleException;
import pt.ua.tqs.ecodelivery.exception.ResourceNotFoundException;
import pt.ua.tqs.ecodelivery.model.Company;
import pt.ua.tqs.ecodelivery.repository.CompanyRepository;
import pt.ua.tqs.ecodelivery.repository.CourierRepository;

/** Registration and maintenance of the institutional clients (BR7). */
@Service
public class CompanyService {

    private final CompanyRepository companyRepository;
    private final CourierRepository courierRepository;

    public CompanyService(CompanyRepository companyRepository, CourierRepository courierRepository) {
        this.companyRepository = companyRepository;
        this.courierRepository = courierRepository;
    }

    @Transactional
    public Company create(CompanyRequest request) {
        String name = request.name().trim();
        if (companyRepository.existsByNameIgnoreCase(name)) {
            throw new BusinessRuleException("Company '" + name + "' already exists");
        }
        Company company = new Company();
        company.setName(name);
        return companyRepository.save(company);
    }

    public List<Company> findAll() {
        return companyRepository.findAll();
    }

    public Company get(Long id) {
        return companyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Company " + id + " not found"));
    }

    @Transactional
    public Company update(Long id, CompanyRequest request) {
        Company company = get(id);
        String name = request.name().trim();
        if (!name.equalsIgnoreCase(company.getName()) && companyRepository.existsByNameIgnoreCase(name)) {
            throw new BusinessRuleException("Company '" + name + "' already exists");
        }
        company.setName(name);
        return companyRepository.save(company);
    }

    @Transactional
    public void delete(Long id) {
        Company company = get(id);
        if (!courierRepository.findByCompanyId(id).isEmpty()) {
            throw new BusinessRuleException("Company '" + company.getName() + "' still has couriers");
        }
        companyRepository.delete(company);
    }
}
