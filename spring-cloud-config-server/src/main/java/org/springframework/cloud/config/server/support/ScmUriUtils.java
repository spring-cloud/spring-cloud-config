/*
 * Copyright 2013-present the original author or authors.
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

package org.springframework.cloud.config.server.support;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Utilities for handling the uris of the repositories the config server reads from.
 *
 * @author Ryan Baxter
 */
public final class ScmUriUtils {

	/**
	 * Replaces each part of the user info of a uri.
	 */
	private static final String MASK = "***";

	/**
	 * Matches scp style ssh uris, for example git@github.com:org/repo.git, capturing
	 * everything after the user.
	 */
	private static final Pattern SCP_LIKE_URI = Pattern.compile("^[^/@:]+@([^/@:]+:.*)$");

	/**
	 * Matches the user info of a hierarchical uri, capturing the scheme that precedes it
	 * and the user info itself.
	 */
	private static final Pattern USER_INFO = Pattern.compile("^([a-zA-Z][a-zA-Z0-9+.\\-]*://)([^/?#]*)@");

	private ScmUriUtils() {
	}

	/**
	 * Masks the user info of a repository uri so that any credentials it carries are not
	 * written to logs, error messages or property source names. The credentials are
	 * masked rather than removed so that a uri configured with credentials can still be
	 * told apart from one configured without them. The rest of the uri is left exactly as
	 * it was configured, placeholders and existing encoding included.
	 * @param uri the repository uri (may be null)
	 * @return the uri with its user info masked
	 */
	public static String maskUserInfo(String uri) {
		if (!StringUtils.hasText(uri) || uri.indexOf('@') < 0) {
			return uri;
		}
		Matcher scpLike = SCP_LIKE_URI.matcher(uri);
		if (scpLike.matches()) {
			// scp style uris (git@github.com:org/repo.git) are not valid uris, so the
			// user has to be masked by hand
			return MASK + "@" + scpLike.group(1);
		}
		Matcher userInfo = USER_INFO.matcher(uri);
		if (!userInfo.find()) {
			// the @ is not part of an authority, for example file:///home/ryan@corp/repo
			return uri;
		}
		// the mask is chosen from the uri as it was configured rather than from the
		// parsed user info, which drops everything before the last @ of the authority
		String mask = mask(userInfo.group(2));
		try {
			UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(uri);
			if (StringUtils.hasText(builder.build().getUserInfo())) {
				// build().toUriString() rather than builder.toUriString(), which would
				// also encode the rest of the uri (placeholders included)
				String masked = builder.userInfo(mask).build().toUriString();
				// a password containing an unencoded @ is not parsed as user info, so
				// make sure nothing is left in front of the host
				return USER_INFO.matcher(masked).replaceFirst("$1" + Matcher.quoteReplacement(mask) + "@");
			}
		}
		catch (IllegalArgumentException e) {
			// the uri cannot be parsed, fall through to masking it by hand
		}
		return userInfo.replaceFirst("$1" + Matcher.quoteReplacement(mask) + "@");
	}

	private static String mask(String userInfo) {
		return userInfo.indexOf(':') < 0 ? MASK : MASK + ":" + MASK;
	}

}
