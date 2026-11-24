package pt.ua.tqs.ecodelivery.bdd;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;

@Suite
@SelectClasspathResource("features/vehicle_lifecycle.feature")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "pt.ua.tqs.ecodelivery.bdd")
class VehicleLifecycleTest {
}
