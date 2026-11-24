package pt.ua.tqs.ecodelivery;

import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class EcoDeliveryApplication {

    public static void main(String[] args) {
        SpringApplication.run(EcoDeliveryApplication.class, args);
    }

    /** The application's notion of "now". Tests replace it with a fixed clock (@TestBean, Lab 03). */
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
