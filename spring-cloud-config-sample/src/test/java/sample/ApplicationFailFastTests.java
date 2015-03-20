package sample;

import org.junit.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;

import static org.junit.Assert.*;

public class ApplicationFailFastTests {

	@Test
	public void contextFails() {
        try {
            new SpringApplicationBuilder()
                    .sources(Application.class)
                    .run("--server.port=0", "--spring.cloud.config.enabled=true", 
                            "--spring.cloud.config.failFast=true",
                            "--spring.cloud.config.uri=http://server-host-doesnt-exist:1234");
            fail("failFast option did not produce an exception");
        } catch (Exception e) {
            assertTrue("Exception not caused by fail fast", e.getMessage().contains("fail fast"));
        }
    }

}
