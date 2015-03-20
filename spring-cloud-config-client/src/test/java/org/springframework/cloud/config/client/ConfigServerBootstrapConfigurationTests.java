package org.springframework.cloud.config.client;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.bootstrap.config.PropertySourceBootstrapConfiguration;
import org.springframework.cloud.config.client.ConfigClientAutoConfiguration;
import org.springframework.cloud.config.client.ConfigClientProperties;
import org.springframework.cloud.config.client.ConfigServerHealthIndicator;
import org.springframework.cloud.config.client.ConfigServiceBootstrapConfiguration;
import org.springframework.context.ConfigurableApplicationContext;

public class ConfigServerBootstrapConfigurationTests {

	@Test
	public void withHealthIndicator() {
		ConfigurableApplicationContext context = new SpringApplicationBuilder(
				PropertySourceBootstrapConfiguration.class, ConfigServiceBootstrapConfiguration.class)
				.child(ConfigClientAutoConfiguration.class).web(false).run();
		assertEquals(1, BeanFactoryUtils.beanNamesForTypeIncludingAncestors(context,
				ConfigClientProperties.class).length);
		assertEquals(1, BeanFactoryUtils.beanNamesForTypeIncludingAncestors(context,
				ConfigServerHealthIndicator.class).length);
		context.close();
	}

}
