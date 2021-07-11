/*
 * Copyright 2013-2021 the original author or authors.
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

import org.springframework.cloud.config.server.resource.ResourceRepository;
import org.springframework.core.io.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.parameters.P;

/**
 * Security meta interface for ResourceRepository.
 *
 * @author ian
 */
public interface ResRepoWithSecurity extends ResourceRepository {

	@PreAuthorize("hasAnyAuthority(this.getAuthorities(#application, #profile, #label, #path))")
	@Override
	Resource findOne(@P("application") String application, @P("profile") String profile, @P("label") String label,
			@P("path") String path);

	String[] getAuthorities(String application, String profile, String label, String path);

	static ResourceRepository create(ResourceRepository delegate, AuthorityExtractor extractor) {
		return new ResRepoWithSecurity() {
			@Override
			public Resource findOne(String application, String profile, String label, String path) {
				return delegate.findOne(application, profile, label, path);
			}

			@Override
			public String[] getAuthorities(String application, String profile, String label, String path) {
				ResourceAccessRequest request = new ResourceAccessRequest();
				request.setType(AccessType.RESOURCE);
				request.setApplication(application);
				request.setProfile(profile);
				request.setLabel(label);
				request.setPath(path);
				return extractor.extract(request);
			}
		};
	}

}
