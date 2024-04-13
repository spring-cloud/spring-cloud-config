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

	private final boolean forceRefresh;

	private RequestContext(Builder builder) {
		this.forceRefresh = builder.forceRefresh;
	}

	public boolean getForceRefresh() {
		return forceRefresh;
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
		return getForceRefresh() == ctx.getForceRefresh();
	}

	@Override
	public int hashCode() {
		return 31 * Objects.hash(getForceRefresh());
	}

	public static class Builder {

		private boolean forceRefresh;

		public Builder forceRefresh(boolean forceRefresh) {
			this.forceRefresh = forceRefresh;
			return this;
		}

		public RequestContext build() {
			return new RequestContext(this);
		}

	}

}
