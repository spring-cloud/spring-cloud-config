package org.springframework.cloud.config.client;

import org.junit.Test;

import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.bootstrap.config.PropertySourceBootstrapConfiguration;
import org.springframework.context.ConfigurableApplicationContext;

import static org.junit.Assert.assertEquals;

public class ConfigServerBootstrapConfigurationTests {

	@Test
	public void withHealthIndicator() {
		ConfigurableApplicationContext context = new SpringApplicationBuilder(
				PropertySourceBootstrapConfiguration.class, ConfigServiceBootstrapConfiguration.class)
				.child(ConfigClientAutoConfiguration.class).web(WebApplicationType.NONE).run();
		assertEquals(1, BeanFactoryUtils.beanNamesForTypeIncludingAncestors(context,
				ConfigClientProperties.class).length);
		assertEquals(1, BeanFactoryUtils.beanNamesForTypeIncludingAncestors(context,
				ConfigServerHealthIndicator.class).length);
		context.close();
	}

}
