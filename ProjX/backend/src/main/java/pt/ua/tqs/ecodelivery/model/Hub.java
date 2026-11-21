package pt.ua.tqs.ecodelivery.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A physical hub where cargo e-bikes are parked, picked up and returned. */
@Entity
@Table(name = "hubs")
public class Hub {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private String address;

    private double latitude;

    private double longitude;

    private int capacity;

    private double boundaryRadiusMeters = 50.0;

    public Hub() {
        // required by JPA
    }

    public Hub(String name, String address, double latitude, double longitude, int capacity, double boundaryRadiusMeters) {
        this.name = name;
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
        this.capacity = capacity;
        this.boundaryRadiusMeters = boundaryRadiusMeters;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public double getBoundaryRadiusMeters() {
        return boundaryRadiusMeters;
    }

    public void setBoundaryRadiusMeters(double boundaryRadiusMeters) {
        this.boundaryRadiusMeters = boundaryRadiusMeters;
    }
}
