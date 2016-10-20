/*
 * Copyright 2013-2015 the original author or authors.
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
package org.springframework.cloud.config.server.support;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cloud.config.environment.Environment;

/**
 * @author: akaala
 */
public class ConfigServerCacheService {

    private static Log logger = LogFactory.getLog(ConfigServerCacheService.class);

    @Cacheable(value = "environment", key = "#name+','+#profiles+','+#label")
    public Environment getEnvironment(String name, String profiles, String label) {
        logger.info("Cache missed, load data cache for: " + name + "," + profiles + "," + label);
        return null;
    }

    @CachePut(value = "environment", key = "#name+','+#profiles+','+#label")
    public Environment putEnvironmentCache(Environment environment, String name, String profiles, String label) {
        logger.info("Put cache for: " + name + "," + profiles + "," + label) ;

        return environment;
    }
}
