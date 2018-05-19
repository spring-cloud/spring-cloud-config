package org.springframework.cloud.config.server.environment;

import org.junit.Test;

import static org.junit.Assert.*;

public class VaultKvAccessStrategyFactoryTest {

    @Test
    public void testGetV1Strategy() {
        final VaultKvAccessStrategy vaultKvAccessStrategy = VaultKvAccessStrategyFactory.forVersion(1);
        assertTrue(vaultKvAccessStrategy instanceof V1VaultKvAccessStrategy);
    }

    @Test
    public void testGetV2Strategy() {
        final VaultKvAccessStrategy vaultKvAccessStrategy = VaultKvAccessStrategyFactory.forVersion(2);
        assertTrue(vaultKvAccessStrategy instanceof V2VaultKvAccessStrategy);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetUnsupportedStrategy() {
        VaultKvAccessStrategyFactory.forVersion(0);
        fail();
    }

}