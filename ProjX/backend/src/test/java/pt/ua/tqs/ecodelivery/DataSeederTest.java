package pt.ua.tqs.ecodelivery;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pt.ua.tqs.ecodelivery.model.Bike;
import pt.ua.tqs.ecodelivery.model.Company;
import pt.ua.tqs.ecodelivery.model.Courier;
import pt.ua.tqs.ecodelivery.model.Hub;
import pt.ua.tqs.ecodelivery.repository.BikeRepository;
import pt.ua.tqs.ecodelivery.repository.CompanyRepository;
import pt.ua.tqs.ecodelivery.repository.CourierRepository;
import pt.ua.tqs.ecodelivery.repository.HubRepository;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DataSeederTest {

    @Mock
    private CompanyRepository companyRepository;
    @Mock
    private CourierRepository courierRepository;
    @Mock
    private HubRepository hubRepository;
    @Mock
    private BikeRepository bikeRepository;

    private DataSeeder seeder;

    @BeforeEach
    void setUp() {
        seeder = new DataSeeder(companyRepository, courierRepository, hubRepository, bikeRepository);
        when(hubRepository.save(any(Hub.class))).then(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("An empty database is filled with a company, couriers, hubs and bikes")
    void seedsEmptyDatabase() {
        seeder.run();

        verify(companyRepository).save(any(Company.class));
        verify(courierRepository, times(3)).save(any(Courier.class));
        verify(hubRepository, times(2)).save(any(Hub.class));
        verify(bikeRepository, times(4)).save(any(Bike.class));
    }

    @Test
    @DisplayName("A database that already has data is left untouched")
    void skipsWhenDataExists() {
        when(companyRepository.count()).thenReturn(1L);

        seeder.run();

        verify(companyRepository, never()).save(any());
        verify(bikeRepository, never()).save(any());
    }

    @Test
    @DisplayName("Existing hubs are enough to skip seeding")
    void skipsWhenHubsExist() {
        when(hubRepository.count()).thenReturn(2L);

        seeder.run();

        verify(courierRepository, never()).save(any());
    }
}
