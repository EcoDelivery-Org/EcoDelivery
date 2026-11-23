package pt.ua.tqs.ecodelivery.service;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pt.ua.tqs.ecodelivery.dto.HubRequest;
import pt.ua.tqs.ecodelivery.exception.BusinessRuleException;
import pt.ua.tqs.ecodelivery.exception.ResourceNotFoundException;
import pt.ua.tqs.ecodelivery.model.Bike;
import pt.ua.tqs.ecodelivery.model.BikeStatus;
import pt.ua.tqs.ecodelivery.model.Coordinates;
import pt.ua.tqs.ecodelivery.model.Hub;
import pt.ua.tqs.ecodelivery.policy.PolicyService;
import pt.ua.tqs.ecodelivery.port.GeocodingPort;
import pt.ua.tqs.ecodelivery.repository.BikeRepository;
import pt.ua.tqs.ecodelivery.repository.HubRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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

    @Mock
    private PolicyService policyService;

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

    // ----------------- US08: Hub Search & Availability Discovery -----------------

    /** Origin of the Hub Finder searches (centre of Aveiro). */
    private static final Coordinates ORIGIN = new Coordinates(40.6405, -8.6538);

    /** One degree of latitude on the mean Earth radius (6371 km), in km. */
    private static final double KM_PER_DEGREE_LAT = 111.195;

    private Bike availableBike(Long id, int batteryLevel) {
        Bike bike = new Bike("EB-" + id, "Urban Cargo", 50.0, batteryLevel, BikeStatus.AVAILABLE, true, sampleHub);
        bike.setId(id);
        return bike;
    }

    private Hub hubNorthOfOrigin(Long id, String name, double degreesNorth) {
        Hub hub = new Hub(name, name + ", Aveiro", ORIGIN.latitude() + degreesNorth, ORIGIN.longitude(), 10, 50.0);
        hub.setId(id);
        return hub;
    }

    @Test
    @DisplayName("getAvailableBikes: battery 19% refused, 20% accepted at the 20% minimum (US08, BR1 boundary)")
    void getAvailableBikes_batteryBoundary_BR1() {
        when(hubRepository.findById(1L)).thenReturn(Optional.of(sampleHub));
        when(policyService.getMinBatteryLevel()).thenReturn(20);
        Bike at19 = availableBike(1L, 19);
        Bike at20 = availableBike(2L, 20);
        Bike at21 = availableBike(3L, 21);
        when(bikeRepository.findByCurrentHubIdAndStatusAndActiveTrue(1L, BikeStatus.AVAILABLE))
                .thenReturn(List.of(at19, at20, at21));

        List<Bike> bikes = hubService.getAvailableBikes(1L);

        assertThat(bikes).containsExactlyInAnyOrder(at20, at21).doesNotContain(at19);
    }

    @Test
    @DisplayName("getAvailableBikes: minimum battery comes from the usage policy, not a fixed value (US08, BR1)")
    void getAvailableBikes_usesConfiguredMinimumBattery_BR1() {
        when(hubRepository.findById(1L)).thenReturn(Optional.of(sampleHub));
        when(policyService.getMinBatteryLevel()).thenReturn(50);
        Bike at49 = availableBike(1L, 49);
        Bike at50 = availableBike(2L, 50);
        when(bikeRepository.findByCurrentHubIdAndStatusAndActiveTrue(1L, BikeStatus.AVAILABLE))
                .thenReturn(List.of(at49, at50));

        List<Bike> bikes = hubService.getAvailableBikes(1L);

        assertThat(bikes).containsExactly(at50);
    }

    @Test
    @DisplayName("getAvailableBikes: only AVAILABLE and active bikes are queried (US08, BR2)")
    void getAvailableBikes_queriesOnlyAvailableAndActiveBikes_BR2() {
        when(hubRepository.findById(1L)).thenReturn(Optional.of(sampleHub));
        when(policyService.getMinBatteryLevel()).thenReturn(20);
        when(bikeRepository.findByCurrentHubIdAndStatusAndActiveTrue(1L, BikeStatus.AVAILABLE)).thenReturn(List.of());

        hubService.getAvailableBikes(1L);

        verify(bikeRepository).findByCurrentHubIdAndStatusAndActiveTrue(1L, BikeStatus.AVAILABLE);
        verify(bikeRepository, never()).findByCurrentHubId(anyLong());
        verify(bikeRepository, never()).findByCurrentHubIdAndStatus(anyLong(), any());
    }

    @Test
    @DisplayName("getAvailableBikes: sorted by battery, highest first, so the courier can compare (US08, Scenario A)")
    void getAvailableBikes_sortedByBatteryDescending() {
        when(hubRepository.findById(1L)).thenReturn(Optional.of(sampleHub));
        when(policyService.getMinBatteryLevel()).thenReturn(20);
        Bike at40 = availableBike(1L, 40);
        Bike at95 = availableBike(2L, 95);
        Bike at60 = availableBike(3L, 60);
        when(bikeRepository.findByCurrentHubIdAndStatusAndActiveTrue(1L, BikeStatus.AVAILABLE))
                .thenReturn(List.of(at40, at95, at60));

        List<Bike> bikes = hubService.getAvailableBikes(1L);

        assertThat(bikes).extracting(Bike::getBatteryLevel).containsExactly(95, 60, 40);
    }

    @Test
    @DisplayName("getAvailableBikes: returns an empty list when no bike can be booked at the hub (US08)")
    void getAvailableBikes_noBookableBikes_returnsEmpty() {
        when(hubRepository.findById(1L)).thenReturn(Optional.of(sampleHub));
        when(policyService.getMinBatteryLevel()).thenReturn(20);
        when(bikeRepository.findByCurrentHubIdAndStatusAndActiveTrue(1L, BikeStatus.AVAILABLE))
                .thenReturn(List.of(availableBike(1L, 5)));

        assertThat(hubService.getAvailableBikes(1L)).isEmpty();
    }

    @Test
    @DisplayName("getAvailableBikes: throws ResourceNotFoundException for an unknown hub (US08)")
    void getAvailableBikes_unknownHub_throwsNotFound() {
        when(hubRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> hubService.getAvailableBikes(99L))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(bikeRepository);
    }

    @Test
    @DisplayName("findNearbyHubs: returns only hubs within the radius, nearest first (US08)")
    void findNearbyHubs_withinRadius_sortedByDistance() {
        when(geocodingPort.geocode("Rossio, Aveiro")).thenReturn(Optional.of(ORIGIN));
        when(policyService.getMinBatteryLevel()).thenReturn(20);
        Hub far = hubNorthOfOrigin(1L, "Hub Far", 0.10);        // ~11.1 km
        Hub near = hubNorthOfOrigin(2L, "Hub Near", 0.01);      // ~1.1 km
        Hub here = hubNorthOfOrigin(3L, "Hub Here", 0.0);       // 0 km
        when(hubRepository.findAll()).thenReturn(List.of(far, near, here));
        when(bikeRepository.findByCurrentHubIdAndStatusAndActiveTrue(anyLong(), any())).thenReturn(List.of());

        List<NearbyHub> result = hubService.findNearbyHubs("Rossio, Aveiro", 2.0);

        assertThat(result).extracting(NearbyHub::hub).containsExactly(here, near);
    }

    @Test
    @DisplayName("findNearbyHubs: distance is the great-circle distance in km (US08)")
    void findNearbyHubs_computesDistanceInKm() {
        when(geocodingPort.geocode("Rossio, Aveiro")).thenReturn(Optional.of(ORIGIN));
        when(policyService.getMinBatteryLevel()).thenReturn(20);
        when(hubRepository.findAll()).thenReturn(List.of(hubNorthOfOrigin(1L, "Hub Near", 0.01)));
        when(bikeRepository.findByCurrentHubIdAndStatusAndActiveTrue(anyLong(), any())).thenReturn(List.of());

        List<NearbyHub> result = hubService.findNearbyHubs("Rossio, Aveiro", 5.0);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).distanceKm()).isCloseTo(0.01 * KM_PER_DEGREE_LAT, within(0.005));
    }

    @Test
    @DisplayName("findNearbyHubs: a hub just inside the radius is included, just outside is excluded (US08)")
    void findNearbyHubs_radiusBoundary() {
        when(geocodingPort.geocode("Rossio, Aveiro")).thenReturn(Optional.of(ORIGIN));
        when(policyService.getMinBatteryLevel()).thenReturn(20);
        Hub hub = hubNorthOfOrigin(1L, "Hub Near", 0.01);       // ~1.112 km
        when(hubRepository.findAll()).thenReturn(List.of(hub));
        when(bikeRepository.findByCurrentHubIdAndStatusAndActiveTrue(anyLong(), any())).thenReturn(List.of());

        assertThat(hubService.findNearbyHubs("Rossio, Aveiro", 1.12)).hasSize(1);
        assertThat(hubService.findNearbyHubs("Rossio, Aveiro", 1.10)).isEmpty();
    }

    @Test
    @DisplayName("findNearbyHubs: counts only bookable bikes per hub, applying BR1 (US08)")
    void findNearbyHubs_countsOnlyBookableBikes_BR1() {
        when(geocodingPort.geocode("Rossio, Aveiro")).thenReturn(Optional.of(ORIGIN));
        when(policyService.getMinBatteryLevel()).thenReturn(20);
        Hub hub = hubNorthOfOrigin(1L, "Hub Here", 0.0);
        when(hubRepository.findAll()).thenReturn(List.of(hub));
        when(bikeRepository.findByCurrentHubIdAndStatusAndActiveTrue(1L, BikeStatus.AVAILABLE))
                .thenReturn(List.of(availableBike(1L, 19), availableBike(2L, 20), availableBike(3L, 80)));

        List<NearbyHub> result = hubService.findNearbyHubs("Rossio, Aveiro", 1.0);

        assertThat(result).singleElement().extracting(NearbyHub::availableBikes).isEqualTo(2);
    }

    @Test
    @DisplayName("findNearbyHubs: returns an empty list when no hub is within the radius (US08)")
    void findNearbyHubs_noHubInRadius_returnsEmpty() {
        when(geocodingPort.geocode("Rossio, Aveiro")).thenReturn(Optional.of(ORIGIN));
        when(hubRepository.findAll()).thenReturn(List.of(hubNorthOfOrigin(1L, "Hub Far", 0.10)));

        assertThat(hubService.findNearbyHubs("Rossio, Aveiro", 2.0)).isEmpty();
    }

    @Test
    @DisplayName("findNearbyHubs: throws BusinessRuleException when the address cannot be geocoded (US08)")
    void findNearbyHubs_geocodingFails_throwsBusinessRuleException() {
        when(geocodingPort.geocode("Nowhere")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> hubService.findNearbyHubs("Nowhere", 2.0))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Unable to geocode address");
        verify(hubRepository, never()).findAll();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("findNearbyHubs: rejects a missing or blank address without geocoding (US08)")
    void findNearbyHubs_blankAddress_throwsIllegalArgument(String address) {
        assertThatThrownBy(() -> hubService.findNearbyHubs(address, 2.0))
                .isInstanceOf(IllegalArgumentException.class);
        verify(geocodingPort, never()).geocode(anyString());
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, -1.0, Double.NaN})
    @DisplayName("findNearbyHubs: rejects a radius that is not greater than zero (US08)")
    void findNearbyHubs_invalidRadius_throwsIllegalArgument(double radiusKm) {
        assertThatThrownBy(() -> hubService.findNearbyHubs("Rossio, Aveiro", radiusKm))
                .isInstanceOf(IllegalArgumentException.class);
        verify(geocodingPort, never()).geocode(anyString());
    }
}
