package org.springframework.cloud.config.server.support;

import org.junit.Test;
import org.springframework.cloud.config.server.config.ConfigServerProperties;

import static org.junit.Assert.assertTrue;

public class AbstractScmAccessorTest {

    private String userHome = System.getProperty("user.home");
    private String tmpDir = System.getProperty("java.io.tmpdir");
    private ConfigServerProperties serverSettings = new ConfigServerProperties();

    @Test
    public void shouldUseDefaultIfNoBaseDirConfigured() {
        serverSettings.setBaseDir(userHome);
        AbstractScmAccessor tested = new AbstractScmAccessor(null, serverSettings);

        assertTrue(tested.getBasedir().getAbsolutePath().startsWith(userHome));
    }

    @Test
    public void shouldUseUriAsSubfolderName() {
        AbstractScmAccessor tested = new AbstractScmAccessor(null, serverSettings);
        tested.setUri("https://repo.com/scm/rp/repo.git");
        serverSettings.setBaseDir(userHome);

        assertTrue(tested.getBasedir().getAbsolutePath().startsWith(userHome));
        assertTrue(tested.getBasedir().getAbsolutePath().endsWith("https___repo_com_scm_rp_repo_git"));
    }

    @Test
    public void shouldNotFailIfNoSettingsPassed() {
        AbstractScmAccessor tested = new AbstractScmAccessor(null, null);

        assertTrue(tested.getBasedir().getAbsolutePath().startsWith(tmpDir));
    }

    @Test
    public void shouldUseBaseDirConfigured() {
        AbstractScmAccessor tested = new AbstractScmAccessor(null, serverSettings);
        serverSettings.setBaseDir(null);

        assertTrue(tested.getBasedir().getAbsolutePath().startsWith(tmpDir));
    }
}