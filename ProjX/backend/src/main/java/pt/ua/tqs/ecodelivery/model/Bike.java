package pt.ua.tqs.ecodelivery.model;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** An electric cargo bike of the fleet. */
@Entity
@Table(name = "e_bikes")
public class Bike {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String serialNumber;

    private String model;

    private double cargoCapacityKg;

    private int batteryLevel;

    @Enumerated(EnumType.STRING)
    private BikeStatus status;

    private boolean active;

    @ManyToOne(fetch = FetchType.LAZY)
    private Hub currentHub;

    public Bike() {
        // required by JPA
    }

    public Bike(String serialNumber, String model, double cargoCapacityKg, int batteryLevel,
                BikeStatus status, boolean active, Hub currentHub) {
        this.serialNumber = serialNumber;
        this.model = model;
        this.cargoCapacityKg = cargoCapacityKg;
        this.batteryLevel = batteryLevel;
        this.status = status;
        this.active = active;
        this.currentHub = currentHub;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public double getCargoCapacityKg() {
        return cargoCapacityKg;
    }

    public void setCargoCapacityKg(double cargoCapacityKg) {
        this.cargoCapacityKg = cargoCapacityKg;
    }

    public int getBatteryLevel() {
        return batteryLevel;
    }

    public void setBatteryLevel(int batteryLevel) {
        this.batteryLevel = batteryLevel;
    }

    public BikeStatus getStatus() {
        return status;
    }

    public void setStatus(BikeStatus status) {
        this.status = status;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Hub getCurrentHub() {
        return currentHub;
    }

    public void setCurrentHub(Hub currentHub) {
        this.currentHub = currentHub;
    }
}
