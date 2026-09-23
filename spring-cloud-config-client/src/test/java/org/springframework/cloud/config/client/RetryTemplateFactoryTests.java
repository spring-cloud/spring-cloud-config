/*
 * Copyright 2014-present the original author or authors.
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

package org.springframework.cloud.config.client;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.logging.Log;
import org.junit.jupiter.api.Test;

import org.springframework.core.retry.RetryTemplate;
import org.springframework.util.backoff.BackOff;
import org.springframework.util.backoff.BackOffExecution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

/**
 * @author Mahammad Eminov
 */
public class RetryTemplateFactoryTests {

	private final Log log = mock(Log.class);

	@Test
	public void maxAttemptsCountsTheInitialInvocation() {
		AtomicInteger executions = new AtomicInteger();

		assertThatThrownBy(() -> create(fastRetry(6)).invoke(() -> {
			executions.incrementAndGet();
			throw new IllegalStateException("boom");
		})).isInstanceOf(IllegalStateException.class);

		assertThat(executions).hasValue(6);
	}

	@Test
	public void singleAttemptDoesNotRetry() {
		AtomicInteger executions = new AtomicInteger();

		assertThatThrownBy(() -> create(fastRetry(1)).invoke(() -> {
			executions.incrementAndGet();
			throw new IllegalStateException("boom");
		})).isInstanceOf(IllegalStateException.class);

		assertThat(executions).hasValue(1);
	}

	@Test
	public void stopsRetryingOnceTheInvocationSucceeds() {
		AtomicInteger executions = new AtomicInteger();

		String result = create(fastRetry(6)).invoke(() -> {
			if (executions.incrementAndGet() < 3) {
				throw new IllegalStateException("boom");
			}
			return "ok";
		});

		assertThat(result).isEqualTo("ok");
		assertThat(executions).hasValue(3);
	}

	@Test
	public void exhaustionPropagatesTheOriginalException() {
		ConfigClientFailFastException failure = new ConfigClientFailFastException("fail fast", null);

		assertThatThrownBy(() -> create(fastRetry(3)).invoke(() -> {
			throw failure;
		})).isSameAs(failure);
	}

	@Test
	public void errorsAreNotRetried() {
		AtomicInteger executions = new AtomicInteger();

		assertThatThrownBy(() -> create(fastRetry(6)).invoke(() -> {
			executions.incrementAndGet();
			throw new AssertionError("boom");
		})).isInstanceOf(AssertionError.class).hasMessage("boom");

		assertThat(executions).hasValue(1);
	}

	@Test
	public void defaultsProduceTheSameIntervalsAsSpringRetry() {
		// 1000ms initial interval, multiplied by 1.1, capped at 2000ms
		assertThat(intervals(backOff(new RetryProperties()), 5)).containsExactly(1000L, 1100L, 1210L, 1331L, 1464L);
	}

	@Test
	public void maxIntervalCapsTheBackOff() {
		RetryProperties properties = new RetryProperties();
		properties.setMaxAttempts(5);
		properties.setInitialInterval(100);
		properties.setMultiplier(10);
		properties.setMaxInterval(2000);

		assertThat(intervals(backOff(properties), 4)).containsExactly(100L, 1000L, 2000L, 2000L);
	}

	@Test
	public void randomPolicyRandomizesTheIntervalUpwards() {
		BackOff backOff = backOff(randomRetry(2, 10000));
		List<Long> samples = new ArrayList<>();
		for (int i = 0; i < 100; i++) {
			samples.addAll(intervals(backOff, 1));
		}

		// Spring Retry randomized in [interval, interval * multiplier)
		assertThat(samples).allSatisfy(interval -> assertThat(interval).isBetween(1000L, 1999L));
	}

	@Test
	public void randomPolicyHonorsMaxIntervalAndMaxAttempts() {
		List<Long> intervals = intervals(backOff(randomRetry(3, 1500)), 3);

		assertThat(intervals.subList(0, 2)).allSatisfy(interval -> assertThat(interval).isBetween(1000L, 1500L));
		assertThat(intervals).last().isEqualTo(BackOffExecution.STOP);
	}

	@Test
	public void maxAttemptsMustBePositive() {
		assertThatThrownBy(() -> create(fastRetry(0))).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Number of attempts should be positive");
		assertThatThrownBy(() -> create(fastRetry(-1))).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Number of attempts should be positive");
	}

	@Test
	public void invalidBackOffConfigurationIsRejected() {
		RetryProperties initialIntervalTooSmall = fastRetry(3);
		initialIntervalTooSmall.setInitialInterval(0);
		assertThatThrownBy(() -> create(initialIntervalTooSmall)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Initial interval should be >= 1");

		RetryProperties multiplierTooSmall = fastRetry(3);
		multiplierTooSmall.setMultiplier(1);
		assertThatThrownBy(() -> create(multiplierTooSmall)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Multiplier should be > 1");

		RetryProperties maxIntervalTooSmall = fastRetry(3);
		maxIntervalTooSmall.setMaxInterval(maxIntervalTooSmall.getInitialInterval());
		assertThatThrownBy(() -> create(maxIntervalTooSmall)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Max interval should be > than initial interval");
	}

	@Test
	public void retryCountIsLoggedForEveryAttempt() {
		List<Object> messages = new ArrayList<>();
		given(this.log.isDebugEnabled()).willReturn(true);
		doAnswer(invocation -> messages.add(invocation.getArgument(0))).when(this.log).debug(any());

		assertThatThrownBy(() -> create(fastRetry(4)).invoke(() -> {
			throw new IllegalStateException("boom");
		})).isInstanceOf(IllegalStateException.class);

		assertThat(messages).containsExactly("Retry: count=0", "Retry: count=1", "Retry: count=2", "Retry: count=3");
	}

	private RetryTemplate create(RetryProperties properties) {
		return RetryTemplateFactory.create(properties, this.log);
	}

	private BackOff backOff(RetryProperties properties) {
		return create(properties).getRetryPolicy().getBackOff();
	}

	private List<Long> intervals(BackOff backOff, int count) {
		BackOffExecution execution = backOff.start();
		List<Long> intervals = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			intervals.add(execution.nextBackOff());
		}
		return intervals;
	}

	@SuppressWarnings("removal")
	private RetryProperties randomRetry(int maxAttempts, long maxInterval) {
		RetryProperties properties = new RetryProperties();
		properties.setMaxAttempts(maxAttempts);
		properties.setInitialInterval(1000);
		properties.setMultiplier(2);
		properties.setMaxInterval(maxInterval);
		properties.setUseRandomPolicy(true);
		return properties;
	}

	private RetryProperties fastRetry(int maxAttempts) {
		RetryProperties properties = new RetryProperties();
		properties.setMaxAttempts(maxAttempts);
		properties.setInitialInterval(1);
		properties.setMaxInterval(2);
		return properties;
	}

}
