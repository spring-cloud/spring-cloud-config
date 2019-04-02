package org.springframework.cloud.config.server.environment;

import org.springframework.core.NestedExceptionUtils;

/**
 * Signify upstream that there was an issue creating your environment from the configuration
 */
public class EnvironmentException extends RuntimeException {

	public EnvironmentException(String string) {
		super(string);
	}

	public EnvironmentException(String message, Throwable cause) {
		super(message, cause);
	}

	@Override
	public String getMessage() {
		Throwable throwable = NestedExceptionUtils.getRootCause(this);
		return String.format("Failed to create environment msg='%s' root exception=%s",
			super.getMessage(),
			throwable.getClass().getCanonicalName()
		);
	}
}
