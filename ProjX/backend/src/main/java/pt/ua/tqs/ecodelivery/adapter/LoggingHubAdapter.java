package pt.ua.tqs.ecodelivery.adapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pt.ua.tqs.ecodelivery.port.HubPort;

/**
 * Hub service adapter that only logs the interactions. There is no real hub to talk to yet (real
 * hardware and hub telemetry are out of scope); a real adapter can replace this one without
 * changing the business logic.
 */
@Component
public class LoggingHubAdapter implements HubPort {

    private static final Logger log = LoggerFactory.getLogger(LoggingHubAdapter.class);

    @Override
    public void releaseBike(Long hubId, Long bikeId) {
        log.info("Hub {}: release bike {}", hubId, bikeId);
    }
}
