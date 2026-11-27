package pt.ua.tqs.ecodelivery.model;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;

/** A courier's reservation of a bike, which becomes a usage session when started. */
@Entity
@Table(name = "reservations", indexes = {
        @Index(name = "idx_reservation_bike_status", columnList = "bike_id, status"),
        @Index(name = "idx_reservation_courier_status", columnList = "courier_id, status")
})
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    private Courier courier;

    @ManyToOne(fetch = FetchType.LAZY)
    private Bike bike;

    @ManyToOne(fetch = FetchType.LAZY)
    private Hub startHub;

    @ManyToOne(fetch = FetchType.LAZY)
    private Hub endHub;

    private LocalDateTime plannedStart;

    private LocalDateTime plannedEnd;

    private LocalDateTime actualStart;

    private LocalDateTime actualEnd;

    @Enumerated(EnumType.STRING)
    private ReservationStatus status;

    @Version
    private Long version;

    public Reservation() {
        // required by JPA
    }

    public Long getId() {
        return id;
    }

    public Courier getCourier() {
        return courier;
    }

    public void setCourier(Courier courier) {
        this.courier = courier;
    }

    public Bike getBike() {
        return bike;
    }

    public void setBike(Bike bike) {
        this.bike = bike;
    }

    public Hub getStartHub() {
        return startHub;
    }

    public void setStartHub(Hub startHub) {
        this.startHub = startHub;
    }

    public Hub getEndHub() {
        return endHub;
    }

    public void setEndHub(Hub endHub) {
        this.endHub = endHub;
    }

    public LocalDateTime getPlannedStart() {
        return plannedStart;
    }

    public void setPlannedStart(LocalDateTime plannedStart) {
        this.plannedStart = plannedStart;
    }

    public LocalDateTime getPlannedEnd() {
        return plannedEnd;
    }

    public void setPlannedEnd(LocalDateTime plannedEnd) {
        this.plannedEnd = plannedEnd;
    }

    public LocalDateTime getActualStart() {
        return actualStart;
    }

    public void setActualStart(LocalDateTime actualStart) {
        this.actualStart = actualStart;
    }

    public LocalDateTime getActualEnd() {
        return actualEnd;
    }

    public void setActualEnd(LocalDateTime actualEnd) {
        this.actualEnd = actualEnd;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public void setStatus(ReservationStatus status) {
        this.status = status;
    }
}
