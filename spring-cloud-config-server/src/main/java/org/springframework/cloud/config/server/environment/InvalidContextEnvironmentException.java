package org.springframework.cloud.config.server.environment;

public class InvalidContextEnvironmentException extends EnvironmentException {

	public InvalidContextEnvironmentException(String string) {
		super(string);
	}

	public InvalidContextEnvironmentException(String message, Throwable cause) {
		super(message, cause);
	}
}
