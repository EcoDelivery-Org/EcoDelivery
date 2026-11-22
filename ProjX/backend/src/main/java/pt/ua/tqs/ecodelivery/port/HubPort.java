package pt.ua.tqs.ecodelivery.port;

/** Port to the hub service: the platform asks a hub to release (unlock) a bike parked there. */
public interface HubPort {

    /** Asks the hub {@code hubId} to release the bike {@code bikeId}. */
    void releaseBike(Long hubId, Long bikeId);
}
