package org.springframework.cloud.config.server;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Answers;
import org.mockito.Mock;
import org.springframework.boot.actuate.health.Status;
import org.springframework.cloud.config.environment.Environment;

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
}
