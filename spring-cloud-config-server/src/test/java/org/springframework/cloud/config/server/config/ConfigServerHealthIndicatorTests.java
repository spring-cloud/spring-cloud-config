/*
 * Copyright 2018-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.config.server.config;

import java.util.Collections;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.Mockito;

import org.springframework.boot.actuate.health.Status;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.server.config.ConfigServerHealthIndicator.Repository;
import org.springframework.cloud.config.server.environment.EnvironmentRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * @author Spencer Gibb
 */
public class ConfigServerHealthIndicatorTests {

	@Mock
	private EnvironmentRepository repository;

	@Mock(answer = Answers.RETURNS_MOCKS)
	private Environment environment;

	private ConfigServerHealthIndicator indicator;

	@Before
	public void init() {
		initMocks(this);
		this.indicator = new ConfigServerHealthIndicator(this.repository);
		this.indicator.init();
	}

	@Test
	public void defaultStatusWorks() {
		when(this.repository.findOne(anyString(), anyString(), Mockito.<String>isNull(),
				anyBoolean())).thenReturn(this.environment);
		assertThat(this.indicator.health().getStatus()).as("wrong default status")
				.isEqualTo(Status.UP);
	}

	@Test
	public void exceptionStatusIsDown() {
		when(this.repository.findOne(anyString(), anyString(), Mockito.<String>isNull(),
				anyBoolean())).thenThrow(new RuntimeException());
		assertThat(this.indicator.health().getStatus()).as("wrong exception status")
				.isEqualTo(Status.DOWN);
	}

	@Test
	public void customLabelWorks() {
		Repository repo = new Repository();
		repo.setName("myname");
		repo.setProfiles("myprofile");
		repo.setLabel("mylabel");
		this.indicator.setRepositories(Collections.singletonMap("myname", repo));
		when(this.repository.findOne("myname", "myprofile", "mylabel", false))
				.thenReturn(this.environment);
		assertThat(this.indicator.health().getStatus()).as("wrong default status")
				.isEqualTo(Status.UP);
	}

}
