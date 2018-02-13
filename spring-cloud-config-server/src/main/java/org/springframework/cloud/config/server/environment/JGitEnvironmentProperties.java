package org.springframework.cloud.config.server.environment;

public class JGitEnvironmentProperties extends AbstractScmEnvironmentProperties {
    private Boolean cloneOnStart;
    private String defaultLabel;
    private Boolean forcePull;
    private Integer timeout;

    public Boolean getCloneOnStart() {
        return cloneOnStart;
    }

    public void setCloneOnStart(Boolean cloneOnStart) {
        this.cloneOnStart = cloneOnStart;
    }

    public String getDefaultLabel() {
        return defaultLabel;
    }

    public void setDefaultLabel(String defaultLabel) {
        this.defaultLabel = defaultLabel;
    }

    public Boolean getForcePull() {
        return forcePull;
    }

    public void setForcePull(Boolean forcePull) {
        this.forcePull = forcePull;
    }

    public Integer getTimeout() {
        return timeout;
    }

    public void setTimeout(Integer timeout) {
        this.timeout = timeout;
    }
}
