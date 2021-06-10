/*
 * Copyright 2013-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.config.server.security;

import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.server.environment.EnvironmentRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.parameters.P;

/**
 * Security meta interface for EnvironmentRepository.
 *
 * @author ian
 */
public interface EnvRepoWithSecurity extends EnvironmentRepository {

	@Override
	@PreAuthorize("hasAnyAuthority(this.getAuthorities(#application, #profile, #label, false))")
	Environment findOne(@P("application") String application, @P("profile") String profile, @P("label") String label);

	@Override
	@PreAuthorize("hasAnyAuthority(this.getAuthorities(#application, #profile, #label, #includeOrigin))")
	Environment findOne(@P("application") String application, @P("profile") String profile, @P("label") String label,
			@P("includeOrigin") boolean includeOrigin);

	String[] getAuthorities(String application, String profile, String label, boolean includeOrigin);

	static EnvironmentRepository create(EnvironmentRepository delegate, AuthorityExtractor extractor) {
		return new EnvRepoWithSecurity() {
			@Override
			public Environment findOne(String application, String profile, String label) {
				return delegate.findOne(application, profile, label);
			}

			@Override
			public Environment findOne(String application, String profile, String label, boolean includeOrigin) {
				return delegate.findOne(application, profile, label, includeOrigin);
			}

			@Override
			public String[] getAuthorities(String application, String profile, String label, boolean includeOrigin) {
				EnvironmentAccessRequest request = new EnvironmentAccessRequest();
				request.setType(AccessType.ENVIRONMENT);
				request.setApplication(application);
				request.setProfile(profile);
				request.setLabel(label);
				request.setIncludeOrigin(includeOrigin);
				return extractor.extract(request);
			}
		};
	}

}
