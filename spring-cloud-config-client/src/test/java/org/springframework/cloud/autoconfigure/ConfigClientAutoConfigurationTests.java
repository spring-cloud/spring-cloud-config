package org.springframework.cloud.autoconfigure;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.bootstrap.config.PropertySourceBootstrapConfiguration;
import org.springframework.cloud.config.client.ConfigClientProperties;
import org.springframework.cloud.config.client.ConfigServerHealthIndicator;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class ConfigClientAutoConfigurationTests {

	@Test
	public void sunnyDay() {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
				ConfigClientAutoConfiguration.class);
		assertEquals(1, BeanFactoryUtils.beanNamesForTypeIncludingAncestors(context,
				ConfigClientProperties.class).length);
		context.close();
	}

	@Test
	public void withParent() {
		ConfigurableApplicationContext context = new SpringApplicationBuilder(
				ConfigClientAutoConfiguration.class).child(Object.class).web(false).run();
		assertEquals(1, BeanFactoryUtils.beanNamesForTypeIncludingAncestors(context,
				ConfigClientProperties.class).length);
		context.close();
	}

	@Test
	public void withHealthIndicator() {
		ConfigurableApplicationContext context = new SpringApplicationBuilder(
				PropertySourceBootstrapConfiguration.class)
				.child(ConfigClientAutoConfiguration.class).web(false).run();
		assertEquals(1, BeanFactoryUtils.beanNamesForTypeIncludingAncestors(context,
				ConfigClientProperties.class).length);
		assertEquals(1, BeanFactoryUtils.beanNamesForTypeIncludingAncestors(context,
				ConfigServerHealthIndicator.class).length);
		context.close();
	}

}
