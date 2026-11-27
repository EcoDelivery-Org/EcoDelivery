package pt.ua.tqs.ecodelivery.controller;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pt.ua.tqs.ecodelivery.dto.CompanyRequest;
import pt.ua.tqs.ecodelivery.dto.CompanyResponse;
import pt.ua.tqs.ecodelivery.model.Company;
import pt.ua.tqs.ecodelivery.service.CompanyService;

/** REST boundary for registered companies (BR7). */
@RestController
@RequestMapping("/api/companies")
public class CompanyController {

    private final CompanyService companyService;

    public CompanyController(CompanyService companyService) {
        this.companyService = companyService;
    }

    @PostMapping
    public ResponseEntity<CompanyResponse> create(@Valid @RequestBody CompanyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(companyService.create(request)));
    }

    @GetMapping
    public List<CompanyResponse> list() {
        return companyService.findAll().stream().map(this::toResponse).toList();
    }

    @GetMapping("/{id}")
    public CompanyResponse get(@PathVariable Long id) {
        return toResponse(companyService.get(id));
    }

    @PutMapping("/{id}")
    public CompanyResponse update(@PathVariable Long id, @Valid @RequestBody CompanyRequest request) {
        return toResponse(companyService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        companyService.delete(id);
        return ResponseEntity.noContent().build();
    }

    private CompanyResponse toResponse(Company company) {
        return new CompanyResponse(company.getId(), company.getName());
    }
}
