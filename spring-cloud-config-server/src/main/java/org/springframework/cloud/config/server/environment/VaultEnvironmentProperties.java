package org.springframework.cloud.config.server.environment;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("spring.cloud.config.server.vault")
public class VaultEnvironmentProperties {
    private String host;
    private Integer port;
    private String scheme;
    private String backend;
    private String defaultKey;
    private String profileSeparator;
    private Integer order;

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public String getScheme() {
        return scheme;
    }

    public void setScheme(String scheme) {
        this.scheme = scheme;
    }

    public String getBackend() {
        return backend;
    }

    public void setBackend(String backend) {
        this.backend = backend;
    }

    public String getDefaultKey() {
        return defaultKey;
    }

    public void setDefaultKey(String defaultKey) {
        this.defaultKey = defaultKey;
    }

    public String getProfileSeparator() {
        return profileSeparator;
    }

    public void setProfileSeparator(String profileSeparator) {
        this.profileSeparator = profileSeparator;
    }

    public Integer getOrder() {
        return order;
    }

    public void setOrder(Integer order) {
        this.order = order;
    }
}
