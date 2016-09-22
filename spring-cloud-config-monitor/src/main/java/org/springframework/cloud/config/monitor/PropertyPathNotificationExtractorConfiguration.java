/*
 * Copyright 2016 the original author or authors.
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

package org.springframework.cloud.config.monitor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Configure the default set of PropertyPathNotificationExtractor beans.
 * 
 * @author whboyd
 */
public class PropertyPathNotificationExtractorConfiguration {

        @Bean
        @ConditionalOnProperty(value="spring.cloud.config.server.monitor.github.enabled", havingValue="true", matchIfMissing=true)
        public GithubPropertyPathNotificationExtractor githubPropertyPathNotificationExtractor() {
                return new GithubPropertyPathNotificationExtractor();
        }

        @Bean
        @ConditionalOnProperty(value="spring.cloud.config.server.monitor.gitlab.enabled", havingValue="true", matchIfMissing=true)
        public GitlabPropertyPathNotificationExtractor gitlabPropertyPathNotificationExtractor() {
                return new GitlabPropertyPathNotificationExtractor();
        }

        @Bean
        @ConditionalOnProperty(value="spring.cloud.config.server.monitor.bitbucket.enabled", havingValue="true", matchIfMissing=true)
        public BitbucketPropertyPathNotificationExtractor bitbucketPropertyPathNotificationExtractor() {
                return new BitbucketPropertyPathNotificationExtractor();
        }
    
}
