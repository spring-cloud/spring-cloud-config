package org.springframework.cloud.config.server.environment;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("spring.cloud.config.server.native")
public class NativeEnvironmentProperties {
    private Boolean failOnError;
    private Boolean addLabelLocations;
    private String defaultLabel;
    private String[] searchLocations;
    private String version;
    private Integer order;

    public Boolean getFailOnError() {
        return failOnError;
    }

    public void setFailOnError(Boolean failOnError) {
        this.failOnError = failOnError;
    }

    public Boolean getAddLabelLocations() {
        return addLabelLocations;
    }

    public void setAddLabelLocations(Boolean addLabelLocations) {
        this.addLabelLocations = addLabelLocations;
    }

    public String getDefaultLabel() {
        return defaultLabel;
    }

    public void setDefaultLabel(String defaultLabel) {
        this.defaultLabel = defaultLabel;
    }

    public String[] getSearchLocations() {
        return searchLocations;
    }

    public void setSearchLocations(String[] searchLocations) {
        this.searchLocations = searchLocations;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public Integer getOrder() {
        return order;
    }

    public void setOrder(Integer order) {
        this.order = order;
    }
}
