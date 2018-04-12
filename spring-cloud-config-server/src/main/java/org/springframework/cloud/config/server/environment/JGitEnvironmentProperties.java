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

    /** Flag to indicate that the repository should be cloned on startup (not on demand). Generally leads to slower startup but faster first query. */
    private boolean cloneOnStart = false;

    /** Flag to indicate that the repository should force pull. If true discard any local changes and take from remote repository. */
    private boolean forcePull;

    /** Timeout (in seconds) for obtaining HTTP or SSH connection (if applicable), defaults to 5 seconds. */
    private int timeout = 5;

    /** Flag to indicate that the branch should be deleted locally if it's origin tracked branch was removed. */
    private boolean deleteUntrackedBranches = false;

    /**
     * Time (in seconds) between refresh of the git repository
     */
    private int refreshRate = 0;

    public JGitEnvironmentProperties() {
        super();
        setDefaultLabel(DEFAULT_LABEL);
    }

    public boolean isCloneOnStart() {
        return cloneOnStart;
    }

    public void setCloneOnStart(boolean cloneOnStart) {
        this.cloneOnStart = cloneOnStart;
    }

    public boolean isForcePull() {
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

    public boolean isDeleteUntrackedBranches() {
        return deleteUntrackedBranches;
    }

    public void setDeleteUntrackedBranches(boolean deleteUntrackedBranches) {
        this.deleteUntrackedBranches = deleteUntrackedBranches;
    }

    public int getRefreshRate() {
        return refreshRate;
    }

    public void setRefreshRate(int refreshRate) {
        this.refreshRate = refreshRate;
    }
}
