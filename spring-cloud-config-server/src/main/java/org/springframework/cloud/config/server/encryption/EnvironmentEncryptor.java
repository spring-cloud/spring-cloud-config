package org.springframework.cloud.config.server.encryption;

import org.springframework.cloud.config.environment.Environment;

public interface EnvironmentEncryptor {
    Environment decrypt(Environment environment);
}
