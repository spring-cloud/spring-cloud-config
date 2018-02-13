package org.springframework.cloud.config.server.environment;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("spring.cloud.config.server.svn")
public class SvnKitEnvironmentProperties extends AbstractScmEnvironmentProperties {
    private String defaultLabel;

    public String getDefaultLabel() {
        return defaultLabel;
    }

    public void setDefaultLabel(String defaultLabel) {
        this.defaultLabel = defaultLabel;
    }
}
