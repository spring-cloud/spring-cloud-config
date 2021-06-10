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

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

/**
 * Default implementation of AuthorityExtractor.
 *
 * Using templates to generate authorities.
 *
 * <table>
 *     <tr><th>Uri path</th><th>Description</th><th>Authorities (any of them)</th></tr>
 *     <tr><td>
 *         /{label}/{application}-{profile}.(json|properties|yaml|yml) or /{application}/{profile}/{label}
 *     </td><td>
 *         Read envirionment for application with profile at label.
 *     </td><td>
 *         ENVIRONMENT_{LABEL}_{APPLICATION}, ENVIRONMENT_{LABEL} or ENVIRONMENT_{APPLICATION}_NULL_NULL
 *     </td></tr>
 *     <tr><td>
 *         /{application}-{profile}.(json|properties|yaml|yml) or /{application}/{profile}
 *     </td><td>
 *         Read envirionment for application with profile at default label.
 *     </td><td>
 *         ENVIRONMENT_NULL_{APPLICATION}, ENVIRONMENT_NULL or ENVIRONMENT_NULL_NULL_NULL
 *     </td></tr>
 *
 *     <tr><td>
 *         /{application}/{profile}/{label}/{resource_path}
 *     </td><td>
 *         Read resource_path content for application with profile at label.
 *     </td><td>
 *         RESOURCE_{LABEL}_{APPLICATION}, RESOURCE_{LABEL} or RESOURCE_{APPLICATION}_NULL_NULL
 *     </td></tr>
 *     <tr><td>
 *         /{application}/{profile}/{resource_path}
 *     </td><td>
 *         Read resource_path content for application with profile at default label.
 *     </td><td>
 *         RESOURCE_NULLL_{APPLICATION}, RESOURCE_NULL or RESOURCE_{APPLICATION}_NULL_NULL
 *     </td></tr>
 *
 *     <tr><td>
 *         /(decrypt|encrypt)/{application}/{profile}
 *     </td><td>
 *         Decrypt or encrypt data for application with profile.
 *     </td><td>
 *         ENCRYPT_NULLL_{APPLICATION}, ENCRYPT_NULL or ENCRYPT_{APPLICATION}_{PROFILE}_{DECRYPT|ENCRYPT}
 *     </td></tr>
 * </table>
 *
 * @author ian
 */
public class DefaultAuthorityExtractor implements AuthorityExtractor {

	/**
	 * Default templates.
	 */
	public static final String[] DEFAULT_TEMPLATES = {
		// Application level permission
		"{type}_{label}_{application}",
		// Environment (label) level permission
		"{type}_{label}",
		// encryptor
		"{type}_{application}_{profiles}_{method}"};

	private List<String> authorityTemplates = Arrays.asList(DEFAULT_TEMPLATES);

	@Override
	public String[] extract(AbstractAccessRequest request) {
		Map<String, String> vars = new HashMap<>();
		// Construct template variables map
		ReflectionUtils.doWithFields(request.getClass(), field -> {
			field.setAccessible(true);
			Object o = field.get(request);
			String value = Objects.toString(o, "NULL").toUpperCase(Locale.ROOT);
			vars.put("{" + field.getName() + "}", value);
		}, field -> !Modifier.isStatic(field.getModifiers()) && !Modifier.isFinal(field.getModifiers()));

		// Replace template variables map
		return authorityTemplates.stream().map(pattern -> {
			String authority = pattern;
			for (Map.Entry<String, String> entry : vars.entrySet()) {
				authority = StringUtils.replace(authority, entry.getKey(), entry.getValue());
			}
			// Replace remain unknown variable placeholder
			return authority.replaceAll("\\{[^}]*}", "NULL");
		}).toArray(String[]::new);
	}

	public void setAuthorityTemplates(List<String> authorityTemplates) {
		this.authorityTemplates = authorityTemplates;
	}

}
