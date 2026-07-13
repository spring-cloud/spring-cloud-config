/*
 * Copyright © 2012 Broadcom Inc. and/or its subsidiaries. All Rights Reserved.
 * Copyright 2012-present the original author or authors.
 */

package org.springframework.cloud.config.monitor;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties;
import org.springframework.boot.tomcat.autoconfigure.servlet.TomcatServletWebServerAutoConfiguration;
import org.springframework.boot.web.server.autoconfigure.ServerProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.cloud.bus.BusProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookValidationAutoConfigurationTests {

	// --- filter registration ---

	@Test
	void filterRegisteredByDefault() {
		try (ConfigurableApplicationContext context = context()) {
			assertThat(context.containsBean("webhookValidatorFilter")).isTrue();
		}
	}

	@Test
	void filterNotRegisteredWhenValidationFilterDisabled() {
		try (ConfigurableApplicationContext context = context(
				"spring.cloud.config.server.monitor.validation-filter-enabled=false")) {
			assertThat(context.containsBean("webhookValidatorFilter")).isFalse();
		}
	}

	@Test
	void filterRegisteredBeforeSpringSecurityFilterChain() {
		try (ConfigurableApplicationContext context = context()) {
			FilterRegistrationBean<?> registration = (FilterRegistrationBean<?>) context
				.getBean("webhookValidatorFilter");
			assertThat(registration.getOrder()).isEqualTo(SecurityFilterProperties.DEFAULT_FILTER_ORDER - 1);
		}
	}

	// --- no secrets configured ---

	@Test
	void noValidatorBeansRegisteredWithNoSecrets() {
		try (ConfigurableApplicationContext context = context()) {
			Map<String, WebhookRequestValidator> validators = context.getBeansOfType(WebhookRequestValidator.class);
			assertThat(validators).isEmpty();
		}
	}

	@Test
	void filterUsesInvalidWebhookRequestValidatorWhenNoSecretsConfigured() {
		try (ConfigurableApplicationContext context = context()) {
			WebhookValidatorFilter filter = webhookValidatorFilter(context);
			@SuppressWarnings("unchecked")
			List<WebhookRequestValidator> validators = (List<WebhookRequestValidator>) ReflectionTestUtils
				.getField(filter, "validators");
			assertThat(validators).containsExactly(WebhookRequestValidator.INVALID_WEBHOOK_REQUEST);
		}
	}

	// --- per-provider validator registration ---

	@Test
	void githubValidatorRegisteredWhenSecretConfigured() {
		try (ConfigurableApplicationContext context = context(
				"spring.cloud.config.server.monitor.github.webhookSecret=secret")) {
			assertThat(context.getBeansOfType(GithubWebhookRequestValidator.class)).hasSize(1);
		}
	}

	@Test
	void githubValidatorNotRegisteredWhenValidationDisabled() {
		try (ConfigurableApplicationContext context = context(
				"spring.cloud.config.server.monitor.github.webhookSecret=secret",
				"spring.cloud.config.server.monitor.github.validationEnabled=false")) {
			assertThat(context.getBeansOfType(GithubWebhookRequestValidator.class)).isEmpty();
		}
	}

	@Test
	void githubValidatorNotRegisteredWhenProviderDisabled() {
		try (ConfigurableApplicationContext context = context(
				"spring.cloud.config.server.monitor.github.webhookSecret=secret",
				"spring.cloud.config.server.monitor.github.enabled=false")) {
			assertThat(context.getBeansOfType(GithubWebhookRequestValidator.class)).isEmpty();
		}
	}

	@Test
	void gogsValidatorRegisteredWhenSecretConfigured() {
		try (ConfigurableApplicationContext context = context(
				"spring.cloud.config.server.monitor.gogs.webhookSecret=secret")) {
			assertThat(context.getBeansOfType(GogsWebhookRequestValidator.class)).hasSize(1);
		}
	}

	@Test
	void gogsValidatorNotRegisteredWhenValidationDisabled() {
		try (ConfigurableApplicationContext context = context(
				"spring.cloud.config.server.monitor.gogs.webhookSecret=secret",
				"spring.cloud.config.server.monitor.gogs.validationEnabled=false")) {
			assertThat(context.getBeansOfType(GogsWebhookRequestValidator.class)).isEmpty();
		}
	}

	@Test
	void gogsValidatorNotRegisteredWhenProviderDisabled() {
		try (ConfigurableApplicationContext context = context(
				"spring.cloud.config.server.monitor.gogs.webhookSecret=secret",
				"spring.cloud.config.server.monitor.gogs.enabled=false")) {
			assertThat(context.getBeansOfType(GogsWebhookRequestValidator.class)).isEmpty();
		}
	}

	@Test
	void gitlabValidatorRegisteredWhenSecretConfigured() {
		try (ConfigurableApplicationContext context = context(
				"spring.cloud.config.server.monitor.gitlab.webhookSecret=secret")) {
			assertThat(context.getBeansOfType(GitlabWebhookRequestValidator.class)).hasSize(1);
		}
	}

	@Test
	void gitlabValidatorNotRegisteredWhenValidationDisabled() {
		try (ConfigurableApplicationContext context = context(
				"spring.cloud.config.server.monitor.gitlab.webhookSecret=secret",
				"spring.cloud.config.server.monitor.gitlab.validationEnabled=false")) {
			assertThat(context.getBeansOfType(GitlabWebhookRequestValidator.class)).isEmpty();
		}
	}

	@Test
	void gitlabValidatorNotRegisteredWhenProviderDisabled() {
		try (ConfigurableApplicationContext context = context(
				"spring.cloud.config.server.monitor.gitlab.webhookSecret=secret",
				"spring.cloud.config.server.monitor.gitlab.enabled=false")) {
			assertThat(context.getBeansOfType(GitlabWebhookRequestValidator.class)).isEmpty();
		}
	}

	@Test
	void giteeValidatorRegisteredWhenSecretConfigured() {
		try (ConfigurableApplicationContext context = context(
				"spring.cloud.config.server.monitor.gitee.webhookSecret=secret")) {
			assertThat(context.getBeansOfType(GiteeWebhookRequestValidator.class)).hasSize(1);
		}
	}

	@Test
	void giteeValidatorNotRegisteredWhenValidationDisabled() {
		try (ConfigurableApplicationContext context = context(
				"spring.cloud.config.server.monitor.gitee.webhookSecret=secret",
				"spring.cloud.config.server.monitor.gitee.validationEnabled=false")) {
			assertThat(context.getBeansOfType(GiteeWebhookRequestValidator.class)).isEmpty();
		}
	}

	@Test
	void giteeValidatorNotRegisteredWhenProviderDisabled() {
		try (ConfigurableApplicationContext context = context(
				"spring.cloud.config.server.monitor.gitee.webhookSecret=secret",
				"spring.cloud.config.server.monitor.gitee.enabled=false")) {
			assertThat(context.getBeansOfType(GiteeWebhookRequestValidator.class)).isEmpty();
		}
	}

	@Test
	void giteaValidatorRegisteredWhenSecretConfigured() {
		try (ConfigurableApplicationContext context = context(
				"spring.cloud.config.server.monitor.gitea.webhookSecret=secret")) {
			assertThat(context.getBeansOfType(GiteaWebhookRequestValidator.class)).hasSize(1);
		}
	}

	@Test
	void giteaValidatorNotRegisteredWhenValidationDisabled() {
		try (ConfigurableApplicationContext context = context(
				"spring.cloud.config.server.monitor.gitea.webhookSecret=secret",
				"spring.cloud.config.server.monitor.gitea.validationEnabled=false")) {
			assertThat(context.getBeansOfType(GiteaWebhookRequestValidator.class)).isEmpty();
		}
	}

	@Test
	void giteaValidatorNotRegisteredWhenProviderDisabled() {
		try (ConfigurableApplicationContext context = context(
				"spring.cloud.config.server.monitor.gitea.webhookSecret=secret",
				"spring.cloud.config.server.monitor.gitea.enabled=false")) {
			assertThat(context.getBeansOfType(GiteaWebhookRequestValidator.class)).isEmpty();
		}
	}

	@Test
	void bitbucketValidatorRegisteredWhenSecretConfigured() {
		try (ConfigurableApplicationContext context = context(
				"spring.cloud.config.server.monitor.bitbucket.webhookSecret=secret")) {
			assertThat(context.getBeansOfType(BitbucketWebhookRequestValidator.class)).hasSize(1);
		}
	}

	@Test
	void bitbucketValidatorNotRegisteredWhenValidationDisabled() {
		try (ConfigurableApplicationContext context = context(
				"spring.cloud.config.server.monitor.bitbucket.webhookSecret=secret",
				"spring.cloud.config.server.monitor.bitbucket.validationEnabled=false")) {
			assertThat(context.getBeansOfType(BitbucketWebhookRequestValidator.class)).isEmpty();
		}
	}

	@Test
	void bitbucketValidatorNotRegisteredWhenProviderDisabled() {
		try (ConfigurableApplicationContext context = context(
				"spring.cloud.config.server.monitor.bitbucket.webhookSecret=secret",
				"spring.cloud.config.server.monitor.bitbucket.enabled=false")) {
			assertThat(context.getBeansOfType(BitbucketWebhookRequestValidator.class)).isEmpty();
		}
	}

	@Test
	void allValidatorsRegisteredWhenAllSecretsConfigured() {
		try (ConfigurableApplicationContext context = context(
				"spring.cloud.config.server.monitor.github.webhookSecret=s1",
				"spring.cloud.config.server.monitor.gogs.webhookSecret=s2",
				"spring.cloud.config.server.monitor.gitlab.webhookSecret=s3",
				"spring.cloud.config.server.monitor.gitee.webhookSecret=s4",
				"spring.cloud.config.server.monitor.gitea.webhookSecret=s5",
				"spring.cloud.config.server.monitor.bitbucket.webhookSecret=s6")) {
			assertThat(context.getBeansOfType(WebhookRequestValidator.class)).hasSize(6);
		}
	}

	// --- validation-enabled=false with secret still causes rejection ---

	@Test
	void filterFallsBackToInvalidWhenValidationDisabledForAllProviders() {
		try (ConfigurableApplicationContext context = context(
				"spring.cloud.config.server.monitor.github.webhookSecret=secret",
				"spring.cloud.config.server.monitor.github.validationEnabled=false")) {
			WebhookValidatorFilter filter = webhookValidatorFilter(context);
			@SuppressWarnings("unchecked")
			List<WebhookRequestValidator> validators = (List<WebhookRequestValidator>) ReflectionTestUtils
				.getField(filter, "validators");
			assertThat(validators).containsExactly(WebhookRequestValidator.INVALID_WEBHOOK_REQUEST);
		}
	}

	// --- helpers ---

	private static ConfigurableApplicationContext context(String... properties) {
		String[] fixed = { "server.port=-1" };
		String[] all = new String[fixed.length + properties.length];
		System.arraycopy(fixed, 0, all, 0, fixed.length);
		System.arraycopy(properties, 0, all, fixed.length, properties.length);
		return new SpringApplicationBuilder(BusConfig.class, EnvironmentMonitorAutoConfiguration.class,
				TomcatServletWebServerAutoConfiguration.class, ServerProperties.class,
				PropertyPlaceholderAutoConfiguration.class)
			.properties(all)
			.run();
	}

	private static WebhookValidatorFilter webhookValidatorFilter(ConfigurableApplicationContext context) {
		FilterRegistrationBean<?> registration = (FilterRegistrationBean<?>) context.getBean("webhookValidatorFilter");
		return (WebhookValidatorFilter) registration.getFilter();
	}

	@Configuration(proxyBeanMethods = false)
	static class BusConfig {

		@Bean
		public BusProperties busProperties() {
			return new BusProperties();
		}

	}

}
