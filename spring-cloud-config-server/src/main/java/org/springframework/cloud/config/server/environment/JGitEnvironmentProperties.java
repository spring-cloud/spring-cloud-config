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

import org.springframework.cloud.config.server.support.AbstractScmAccessorProperties;

/**
 * @author Dylan Roberts
 */
public class JGitEnvironmentProperties extends AbstractScmAccessorProperties {
    private static final String DEFAULT_LABEL = "master";

    private boolean cloneOnStart = false;
    private boolean forcePull;
    private int timeout = 5;

    public JGitEnvironmentProperties() {
        super();
        setDefaultLabel(DEFAULT_LABEL);
    }

    public boolean getCloneOnStart() {
        return cloneOnStart;
    }

    public void setCloneOnStart(boolean cloneOnStart) {
        this.cloneOnStart = cloneOnStart;
    }

    public boolean getForcePull() {
        return forcePull;
    }

    public void setForcePull(boolean forcePull) {
        this.forcePull = forcePull;
    }

    public int getTimeout() {
        return timeout;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }
}
