package org.springframework.cloud.config.server.config;

import java.util.Collections;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Answers;
import org.mockito.Mock;
import org.springframework.boot.actuate.health.Status;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.server.config.ConfigServerHealthIndicator.Repository;
import org.springframework.cloud.config.server.environment.EnvironmentRepository;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.anyString;
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
		indicator = new ConfigServerHealthIndicator(repository);
		indicator.init();
	}

	@Test
	public void defaultStatusWorks() {
		when(repository.findOne(anyString(), anyString(), anyString())).thenReturn(environment);
		assertEquals("wrong default status", Status.UP, indicator.health().getStatus());
	}

	@Test
	public void exceptionStatusIsDown() {
		when(repository.findOne(anyString(), anyString(), anyString())).thenThrow(new RuntimeException());
		assertEquals("wrong exception status", Status.DOWN, indicator.health().getStatus());
	}

	@Test
	public void customLabelWorks() {
		Repository repo = new Repository();
		repo.setName("myname");
		repo.setProfiles("myprofile");
		repo.setLabel("mylabel");
		this.indicator.setRepositories(Collections.singletonMap("myname", repo));
		when(repository.findOne("myname", "myprofile", "mylabel")).thenReturn(environment);
		assertEquals("wrong default status", Status.UP, indicator.health().getStatus());
	}
}
