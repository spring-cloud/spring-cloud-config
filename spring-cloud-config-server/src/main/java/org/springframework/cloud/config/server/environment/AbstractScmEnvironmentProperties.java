package org.springframework.cloud.config.server.environment;

import org.springframework.cloud.config.server.support.AbstractScmAccessorProperties;

public abstract class AbstractScmEnvironmentProperties extends AbstractScmAccessorProperties {
    private Integer order;

    public Integer getOrder() {
        return order;
    }

    public void setOrder(Integer order) {
        this.order = order;
    }
}
