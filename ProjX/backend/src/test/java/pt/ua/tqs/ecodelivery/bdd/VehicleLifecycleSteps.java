package pt.ua.tqs.ecodelivery.bdd;

import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import pt.ua.tqs.ecodelivery.exception.BusinessRuleException;
import pt.ua.tqs.ecodelivery.model.Bike;
import pt.ua.tqs.ecodelivery.model.BikeStatus;
import pt.ua.tqs.ecodelivery.model.Hub;
import pt.ua.tqs.ecodelivery.model.Reservation;
import pt.ua.tqs.ecodelivery.model.ReservationStatus;
import pt.ua.tqs.ecodelivery.policy.PolicyService;
import pt.ua.tqs.ecodelivery.repository.BikeEventRepository;
import pt.ua.tqs.ecodelivery.repository.BikeRepository;
import pt.ua.tqs.ecodelivery.repository.HubRepository;
import pt.ua.tqs.ecodelivery.repository.ReservationRepository;
import pt.ua.tqs.ecodelivery.service.BikeService;
import pt.ua.tqs.ecodelivery.service.InventoryLogService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VehicleLifecycleSteps {
    private BikeService service;
    private Bike bike;
    private Reservation reservation;
    private ReservationRepository reservations;
    private RuntimeException error;

    @Before
    public void setUp() {
        BikeRepository bikes = mock(BikeRepository.class);
        reservations = mock(ReservationRepository.class);
        PolicyService policy = mock(PolicyService.class);
        service = new BikeService(bikes, mock(HubRepository.class), mock(BikeEventRepository.class),
                policy, mock(InventoryLogService.class), reservations, Clock.systemUTC());
        Hub hub = new Hub("Central", "Aveiro", 40.64, -8.65, 10, 50);
        hub.setId(1L);
        bike = new Bike("EB-1", "Cargo", 60, 80, BikeStatus.AVAILABLE, true, hub);
        bike.setId(1L);
        when(bikes.findById(1L)).thenReturn(Optional.of(bike));
        when(bikes.save(any(Bike.class))).thenAnswer(call -> call.getArgument(0));
        when(policy.isBatterySufficient(80)).thenReturn(true);
    }

    @Given("an active bicycle in the AVAILABLE state")
    public void activeAvailable() { assertThat(bike.isActive()).isTrue(); }

    @Given("an inactive bicycle in the AVAILABLE state")
    public void inactiveAvailable() { bike.setActive(false); }

    @Given("a bicycle with a pending reservation")
    public void pendingReservation() {
        bike.setStatus(BikeStatus.RESERVED);
        reservation = new Reservation();
        reservation.setBike(bike);
        reservation.setStatus(ReservationStatus.RESERVED);
        when(reservations.findByBikeIdAndStatus(1L, ReservationStatus.RESERVED))
                .thenReturn(List.of(reservation));
    }

    @Given("a retired bicycle")
    public void retired() { bike.setStatus(BikeStatus.RETIRED); }

    @Given("a bicycle with an ongoing usage session")
    public void ongoingSession() {
        bike.setStatus(BikeStatus.IN_USE);
        reservation = new Reservation();
        reservation.setBike(bike);
        reservation.setStatus(ReservationStatus.IN_USE);
    }

    @When("the operator changes its state to MAINTENANCE")
    public void changeToMaintenance() { service.changeStatus(1L, BikeStatus.MAINTENANCE); }

    @When("the operator deactivates the bicycle")
    public void deactivate() { service.deactivate(1L); }

    @When("the operator activates the bicycle")
    public void activate() { service.activate(1L); }

    @When("the operator attempts to change its state to AVAILABLE")
    public void invalidChange() {
        try { service.changeStatus(1L, BikeStatus.AVAILABLE); }
        catch (RuntimeException ex) { error = ex; }
    }

    @Then("the bicycle state should be MAINTENANCE")
    public void maintenanceState() { assertThat(bike.getStatus()).isEqualTo(BikeStatus.MAINTENANCE); }

    @Then("the bicycle should not be available for reservations")
    public void notAvailable() { assertThat(service.isAvailableForReservation(1L)).isFalse(); }

    @Then("the bicycle should be administratively inactive")
    public void inactive() { assertThat(bike.isActive()).isFalse(); }

    @Then("the bicycle should be administratively active")
    public void active() { assertThat(bike.isActive()).isTrue(); }

    @Then("the bicycle lifecycle state should remain AVAILABLE")
    public void stillAvailable() { assertThat(bike.getStatus()).isEqualTo(BikeStatus.AVAILABLE); }

    @Then("the reservation should be CANCELLED")
    public void cancelled() { assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED); }

    @Then("the operation should be rejected")
    public void rejected() { assertThat(error).isInstanceOf(BusinessRuleException.class); }

    @Then("the bicycle should remain RETIRED")
    public void remainsRetired() { assertThat(bike.getStatus()).isEqualTo(BikeStatus.RETIRED); }

    @Then("the ongoing usage session should remain IN_USE")
    public void sessionRemains() { assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.IN_USE); }
}
