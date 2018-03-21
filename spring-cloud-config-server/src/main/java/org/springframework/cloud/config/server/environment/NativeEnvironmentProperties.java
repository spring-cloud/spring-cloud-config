/*
 * Copyright 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.cloud.config.server.environment;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.config.server.support.EnvironmentRepositoryProperties;
import org.springframework.core.Ordered;

/**
 * @author Dylan Roberts
 */
@ConfigurationProperties("spring.cloud.config.server.native")
public class NativeEnvironmentProperties implements EnvironmentRepositoryProperties {
    private Boolean failOnError = false;
    private Boolean addLabelLocations = true;
    private String defaultLabel = "master";
    private String[] searchLocations = new String[0];
    private String version;
    private int order = Ordered.LOWEST_PRECEDENCE;

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

    public int getOrder() {
        return order;
    }

    @Override
    public void setOrder(int order) {
        this.order = order;
    }
}
