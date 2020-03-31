package org.springframework.cloud.config.server.environment.secretManager;

import java.util.Comparator;

import com.google.cloud.secretmanager.v1.Secret;
import com.google.cloud.secretmanager.v1.SecretVersion;


public interface GoogleSecretManagerAccessStrategy {

	Iterable<Secret> getSecrets();

	String getSecretValue(Secret secret, Comparator<SecretVersion> comparator);

	String getSecretName(Secret secret);
}
