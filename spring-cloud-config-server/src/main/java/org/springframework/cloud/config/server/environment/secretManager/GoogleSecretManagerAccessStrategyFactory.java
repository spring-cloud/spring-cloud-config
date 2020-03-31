package org.springframework.cloud.config.server.environment.secretManager;

import java.io.IOException;

import org.springframework.cloud.config.server.environment.RepositoryException;
import org.springframework.web.client.RestTemplate;

public final class GoogleSecretManagerAccessStrategyFactory {

	public static GoogleSecretManagerAccessStrategy forVersion(RestTemplate rest, GoogleConfigProvider configProvider, int version) {

		switch (version) {
		case 1:
			try {
				return new GoogleSecretManagerV1AccessStrategy(rest, configProvider);
			} catch (IOException e) {
				throw new RepositoryException("Cannot create service client", e);
			}
		default:
			throw new IllegalArgumentException(
				"No support for given Google Secret manager backend version " + version);
		}
	}

}
