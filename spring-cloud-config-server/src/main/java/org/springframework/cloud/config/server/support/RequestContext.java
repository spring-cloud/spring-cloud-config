/*
 * Copyright 2013-2024 the original author or authors.
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

import java.util.Objects;

/**
 * @author Yuto Yamada
 */
public final class RequestContext {

	private final String name;

	private final String profiles;

	private final String label;

	private final String path;

	private final boolean resolvePlaceholders;

	/**
	 * includeOrigin does not come from HTTP request but specific to endpoint.
	 */
	private final boolean includeOrigin;

	private RequestContext(Builder builder) {
		this.name = builder.name;
		this.profiles = builder.profiles;
		this.label = builder.label;
		this.path = builder.path;
		this.resolvePlaceholders = builder.resolvePlaceholders;
		this.includeOrigin = builder.includeOrigin;
	}

	public String getName() {
		return name;
	}

	public String getProfiles() {
		return profiles;
	}

	public String getLabel() {
		return label;
	}

	public String getPath() {
		return path;
	}

	public boolean getResolvePlaceholders() {
		return resolvePlaceholders;
	}

	public boolean getIncludeOrigin() {
		return includeOrigin;
	}

	public Builder toBuilder() {
		return new Builder().name(getName()).profiles(getProfiles()).label(getLabel()).path(getPath())
				.resolvePlaceholders(getResolvePlaceholders()).includeOrigin(getIncludeOrigin());
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		RequestContext ctx = (RequestContext) o;
		return (getName() == null ? ctx.getName() == null : getName().equals(ctx.getName()))
				&& (getProfiles() == null ? ctx.getProfiles() == null : getProfiles().equals(ctx.getProfiles()))
				&& (getLabel() == null ? ctx.getLabel() == null : getLabel().equals(ctx.getLabel()))
				&& (getPath() == null ? ctx.getPath() == null : getPath().equals(ctx.getPath()))
				&& getResolvePlaceholders() == ctx.getResolvePlaceholders()
				&& getIncludeOrigin() == ctx.getIncludeOrigin();
	}

	@Override
	public int hashCode() {
		return 31 * Objects.hash(getName(), getProfiles(), getLabel(), getResolvePlaceholders(), getIncludeOrigin());
	}

	public static class Builder {

		private String name = null;

		private String profiles = null;

		private String label = null;

		private String path = null;

		private boolean resolvePlaceholders = false;

		private boolean includeOrigin = false;

		public Builder name(String name) {
			this.name = name;
			return this;
		}

		public Builder profiles(String profiles) {
			this.profiles = profiles;
			return this;
		}

		public Builder label(String label) {
			this.label = label;
			return this;
		}

		public Builder path(String path) {
			this.path = path;
			return this;
		}

		public Builder resolvePlaceholders(boolean resolvePlaceholders) {
			this.resolvePlaceholders = resolvePlaceholders;
			return this;
		}

		public Builder includeOrigin(boolean includeOrigin) {
			this.includeOrigin = includeOrigin;
			return this;
		}

		public RequestContext build() {
			return new RequestContext(this);
		}

	}

}
