package pt.ua.tqs.ecodelivery;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pt.ua.tqs.ecodelivery.model.Bike;
import pt.ua.tqs.ecodelivery.model.BikeStatus;
import pt.ua.tqs.ecodelivery.model.Company;
import pt.ua.tqs.ecodelivery.model.Courier;
import pt.ua.tqs.ecodelivery.model.Hub;
import pt.ua.tqs.ecodelivery.repository.BikeRepository;
import pt.ua.tqs.ecodelivery.repository.CompanyRepository;
import pt.ua.tqs.ecodelivery.repository.CourierRepository;
import pt.ua.tqs.ecodelivery.repository.HubRepository;

/** Loads demo data into an empty database so the platform can be tried out straight away. */
@Component
@ConditionalOnProperty(name = "ecodelivery.seed.enabled", havingValue = "true")
public class DataSeeder implements CommandLineRunner {

    private final CompanyRepository companyRepository;
    private final CourierRepository courierRepository;
    private final HubRepository hubRepository;
    private final BikeRepository bikeRepository;

    public DataSeeder(CompanyRepository companyRepository, CourierRepository courierRepository,
                      HubRepository hubRepository, BikeRepository bikeRepository) {
        this.companyRepository = companyRepository;
        this.courierRepository = courierRepository;
        this.hubRepository = hubRepository;
        this.bikeRepository = bikeRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (companyRepository.count() > 0 || hubRepository.count() > 0) {
            return;
        }
        Company company = new Company();
        company.setName("EcoCargo Lda");
        companyRepository.save(company);

        courier("Ana Silva", "ana.silva@ecocargo.pt", "910000001", company);
        courier("Rui Costa", "rui.costa@ecocargo.pt", "910000002", company);
        courier("Freelancer Sem Empresa", "free@example.com", "910000003", null);

        Hub aveiro = hubRepository.save(new Hub("Hub Aveiro Centro", "Praça do Peixe, Aveiro", 40.6405, -8.6538, 10, 50.0));
        Hub univ = hubRepository.save(new Hub("Hub Universidade", "Campus de Santiago, Aveiro", 40.6302, -8.6579, 8, 50.0));

        bikeRepository.save(new Bike("EB-0001", "Cargo Pro 200", 120, 95, BikeStatus.AVAILABLE, true, aveiro));
        bikeRepository.save(new Bike("EB-0002", "Cargo Pro 200", 120, 60, BikeStatus.AVAILABLE, true, aveiro));
        bikeRepository.save(new Bike("EB-0003", "Cargo Lite 100", 80, 15, BikeStatus.AVAILABLE, true, univ));
        bikeRepository.save(new Bike("EB-0004", "Cargo Lite 100", 80, 100, BikeStatus.MAINTENANCE, true, univ));
    }

    private void courier(String name, String email, String phone, Company company) {
        Courier courier = new Courier();
        courier.setName(name);
        courier.setEmail(email);
        courier.setPhone(phone);
        courier.setCompany(company);
        courierRepository.save(courier);
    }
}
