/*
 * Copyright © 2012 Broadcom Inc. and/or its subsidiaries. All Rights Reserved.
 * Copyright 2012-present the original author or authors.
 */

package org.springframework.cloud.config.server.environment;

import java.util.Collections;

import org.junit.jupiter.api.Test;

import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.cloud.config.environment.PropertyValueDescriptor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link EnvironmentCleaner}, in particular that credentials embedded in the
 * repository URI never make it into a property source name. The uri forms themselves are
 * covered by {@code ScmUriUtilsTests}.
 */
public class EnvironmentCleanerTests {

	private static final String WORKING_DIR = "file:/tmp/config/";

	private final EnvironmentCleaner cleaner = new EnvironmentCleaner();

	private String cleanName(String uri) {
		Environment environment = new Environment("app", "default");
		environment.add(new PropertySource("applicationConfig: [" + WORKING_DIR + "app.properties]",
				Collections.singletonMap("foo", "bar")));
		return this.cleaner.clean(environment, WORKING_DIR, uri).getPropertySources().get(0).getName();
	}

	@Test
	public void usernameAndPasswordAreMasked() {
		assertThat(cleanName("https://ryan:secret@example.com/config-repo.git"))
			.isEqualTo("https://***:***@example.com/config-repo.git/app.properties");
	}

	@Test
	public void uriWithoutCredentialsIsUnchanged() {
		assertThat(cleanName("https://example.com/config-repo.git"))
			.isEqualTo("https://example.com/config-repo.git/app.properties");
	}

	@Test
	public void scpStyleSshUriUserIsMasked() {
		assertThat(cleanName("git@github.com:org/config-repo.git"))
			.isEqualTo("***@github.com:org/config-repo.git/app.properties");
	}

	@Test
	public void credentialsAreMaskedInOrigins() {
		Environment environment = new Environment("app", "default");
		PropertyValueDescriptor descriptor = new PropertyValueDescriptor("bar", "class path resource [app.properties]");
		environment.add(new PropertySource("applicationConfig: [" + WORKING_DIR + "app.properties]",
				Collections.singletonMap("foo", descriptor)));

		Environment result = this.cleaner.clean(environment, WORKING_DIR,
				"https://ryan:secret@example.com/config-repo.git");

		PropertyValueDescriptor cleaned = (PropertyValueDescriptor) result.getPropertySources()
			.get(0)
			.getSource()
			.get("foo");
		assertThat(cleaned.getOrigin()).doesNotContain("ryan").doesNotContain("secret");
		assertThat(cleaned.getOrigin())
			.isEqualTo("class path resource [https://***:***@example.com/config-repo.git/app.properties]");
	}

}
