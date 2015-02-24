package org.springframework.cloud.config.client;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.CompositePropertySource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.backoff.BackOffPolicy;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.NeverRetryPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

@Order(0)
public class RetryableConfigServicePropertySourceLocator extends
		AbstractConfigServicePropertyLocator {

	private static Log logger = LogFactory
			.getLog(RetryableConfigServicePropertySourceLocator.class);

	private RetryTemplate retryTemplate;

	public RetryableConfigServicePropertySourceLocator(ConfigClientProperties defaults) {
		super(defaults);
	}

	@Override
	protected PropertySource<?> tryLocating(final ConfigClientProperties client,
			final CompositePropertySource composite, final Environment environment)
			throws Exception {
		ConfigClientProperties defaults = getDefaults();

		if (defaults.getRetryBeforeFail() == 0) {
			if (logger.isWarnEnabled()) {
				logger.warn("To control retry count you can set the "
						+ ConfigClientProperties.PREFIX + ".retryBeforeFail property");
			}
		}

		RetryTemplate retryTemplate = this.retryTemplate == null ? getRetryTemplate(client)
				: this.retryTemplate;

		return retryTemplate.execute(new RetryCallback<PropertySource<?>, Exception>() {

			@Override
			public PropertySource<?> doWithRetry(RetryContext retryContext)
					throws Exception {
				return exchange(client, composite);
			}
		});
	}

	public void setRetryTemplate(RetryTemplate retryTemplate) {
		this.retryTemplate = retryTemplate;
	}

	private RetryTemplate getRetryTemplate(ConfigClientProperties client) {
		RetryTemplate retryTemplate = new RetryTemplate();
		retryTemplate.setRetryPolicy(new NeverRetryPolicy());

		if (client.getRetryBeforeFail() > 0) {
			SimpleRetryPolicy simpleRetry = new SimpleRetryPolicy();
			simpleRetry.setMaxAttempts(client.getRetryBeforeFail());
			retryTemplate.setRetryPolicy(simpleRetry);

			BackOffPolicy exponentialBackoff = new ExponentialBackOffPolicy();
			retryTemplate.setBackOffPolicy(exponentialBackoff);
		}

		return retryTemplate;
	}

}
