package pt.ua.tqs.ecodelivery.service;

import pt.ua.tqs.ecodelivery.model.Hub;

/** A hub found by the Hub Finder, with its distance to the searched location and its bookable bikes (US08). */
public record NearbyHub(Hub hub, double distanceKm, int availableBikes) {
}
