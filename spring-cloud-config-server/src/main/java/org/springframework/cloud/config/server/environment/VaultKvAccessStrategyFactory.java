package org.springframework.cloud.config.server.environment;

public class VaultKvAccessStrategyFactory {

    public static VaultKvAccessStrategy forVersion(int version) {
        switch (version) {
            case 1:
                return new V1VaultKvAccessStrategy();
            case 2:
                return new V2VaultKvAccessStrategy();
            default:
                throw new IllegalArgumentException("No support for given Vault k/v backend version " + version);
        }
    }
}
