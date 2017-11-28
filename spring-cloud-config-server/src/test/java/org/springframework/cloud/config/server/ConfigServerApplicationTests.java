package org.springframework.cloud.config.server;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.ClassPathExclusions;
import org.springframework.cloud.FilteredClassPathRunner;
import org.springframework.context.ConfigurableApplicationContext;

@RunWith(FilteredClassPathRunner.class)
@ClassPathExclusions("h2-*.jar")
public class ConfigServerApplicationTests {

	@Test
	public void contextLoads() {
		try (ConfigurableApplicationContext context = new SpringApplicationBuilder(ConfigServerApplication.class)
				.properties("spring.config.name=configserver").run()) {

		}
	}

}
