package pt.ua.tqs.ecodelivery.service;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pt.ua.tqs.ecodelivery.dto.HubRequest;
import pt.ua.tqs.ecodelivery.exception.BusinessRuleException;
import pt.ua.tqs.ecodelivery.exception.ResourceNotFoundException;
import pt.ua.tqs.ecodelivery.model.Coordinates;
import pt.ua.tqs.ecodelivery.model.Hub;
import pt.ua.tqs.ecodelivery.port.GeocodingPort;
import pt.ua.tqs.ecodelivery.repository.BikeRepository;
import pt.ua.tqs.ecodelivery.repository.HubRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HubServiceTest {

    @Mock
    private HubRepository hubRepository;

    @Mock
    private BikeRepository bikeRepository;

    @Mock
    private GeocodingPort geocodingPort;

    @Mock
    private InventoryLogService inventoryLogService;

    @InjectMocks
    private HubService hubService;

    private Hub sampleHub;

    @BeforeEach
    void setUp() {
        sampleHub = new Hub("Hub Rossio", "Praça do Rossio, Aveiro", 40.6412, -8.6536, 10, 60.0);
        sampleHub.setId(1L);
    }

    @Test
    @DisplayName("createHub: succeed with explicit coordinates and boundary radius (US01)")
    void createHub_withExplicitCoordinates_success() {
        HubRequest request = new HubRequest("Hub Rossio", "Praça do Rossio, Aveiro", 15, 40.6412, -8.6536, 75.0);
        when(hubRepository.save(any(Hub.class))).thenAnswer(invocation -> {
            Hub h = invocation.getArgument(0);
            h.setId(1L);
            return h;
        });

        Hub created = hubService.createHub(request);

        assertThat(created).isNotNull();
        assertThat(created.getName()).isEqualTo("Hub Rossio");
        assertThat(created.getLatitude()).isEqualTo(40.6412);
        assertThat(created.getLongitude()).isEqualTo(-8.6536);
        assertThat(created.getCapacity()).isEqualTo(15);
        assertThat(created.getBoundaryRadiusMeters()).isEqualTo(75.0);

        verify(hubRepository).save(any(Hub.class));
        verify(inventoryLogService).logHubProvisioned(created);
    }

    @Test
    @DisplayName("createHub: succeed with geocoding when coordinates are omitted (US01)")
    void createHub_withGeocoding_success() {
        HubRequest request = new HubRequest("Hub Univ", "Campus Universitário, Aveiro", 20);
        when(geocodingPort.geocode("Campus Universitário, Aveiro"))
                .thenReturn(Optional.of(new Coordinates(40.6300, -8.6550)));
        when(hubRepository.save(any(Hub.class))).thenAnswer(invocation -> {
            Hub h = invocation.getArgument(0);
            h.setId(2L);
            return h;
        });

        Hub created = hubService.createHub(request);

        assertThat(created).isNotNull();
        assertThat(created.getLatitude()).isEqualTo(40.6300);
        assertThat(created.getLongitude()).isEqualTo(-8.6550);
        assertThat(created.getBoundaryRadiusMeters()).isEqualTo(50.0);
        verify(inventoryLogService).logHubProvisioned(created);
    }

    @Test
    @DisplayName("createHub: throws BusinessRuleException when coordinates omitted and geocoding fails (US01)")
    void createHub_geocodingFails_throwsBusinessRuleException() {
        HubRequest request = new HubRequest("Invalid Hub", "Nowhere Street 123", 10);
        when(geocodingPort.geocode("Nowhere Street 123")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> hubService.createHub(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Unable to geocode address");
    }

    @Test
    @DisplayName("createHub: throws BusinessRuleException when capacity is invalid (US01)")
    void createHub_invalidCapacity_throwsBusinessRuleException() {
        HubRequest request = new HubRequest("Hub Test", "Aveiro", 0, 40.6, -8.6, 50.0);

        assertThatThrownBy(() -> hubService.createHub(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("capacity must be greater than zero");
    }

    @Test
    @DisplayName("createHub: throws BusinessRuleException when name is blank (US01)")
    void createHub_blankName_throwsBusinessRuleException() {
        HubRequest request = new HubRequest("   ", "Aveiro", 10, 40.6, -8.6, 50.0);

        assertThatThrownBy(() -> hubService.createHub(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("name cannot be empty");
    }

    @Test
    @DisplayName("findAll: returns all provisioned hubs (US01)")
    void findAll_returnsHubs() {
        when(hubRepository.findAll()).thenReturn(List.of(sampleHub));

        List<Hub> hubs = hubService.findAll();

        assertThat(hubs).hasSize(1);
        assertThat(hubs.getFirst().getName()).isEqualTo("Hub Rossio");
    }

    @Test
    @DisplayName("getHub: returns hub when found (US01)")
    void getHub_found() {
        when(hubRepository.findById(1L)).thenReturn(Optional.of(sampleHub));

        Hub result = hubService.getHub(1L);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("getHub: throws ResourceNotFoundException when not found (US01)")
    void getHub_notFound_throwsException() {
        when(hubRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> hubService.getHub(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Hub not found with id: 99");
    }

    @Test
    @DisplayName("hasCapacity: returns true when current bike count is below capacity (US01)")
    void hasCapacity_belowCapacity_returnsTrue() {
        when(hubRepository.findById(1L)).thenReturn(Optional.of(sampleHub));
        when(bikeRepository.countByCurrentHubId(1L)).thenReturn(5L);

        boolean hasCap = hubService.hasCapacity(1L);

        assertThat(hasCap).isTrue();
    }

    @Test
    @DisplayName("hasCapacity: returns false when current bike count reaches capacity (US01)")
    void hasCapacity_atCapacity_returnsFalse() {
        when(hubRepository.findById(1L)).thenReturn(Optional.of(sampleHub));
        when(bikeRepository.countByCurrentHubId(1L)).thenReturn(10L);

        boolean hasCap = hubService.hasCapacity(1L);

        assertThat(hasCap).isFalse();
    }
}
