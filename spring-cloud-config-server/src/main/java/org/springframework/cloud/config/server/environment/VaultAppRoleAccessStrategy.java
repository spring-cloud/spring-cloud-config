/*
 * Copyright 2018-2019 the original author or authors.
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

package org.springframework.cloud.config.server.environment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.http.HttpEntity;
import org.springframework.web.client.RestClientException;

/**
 * Strategy interface to obtain token from Vault's App Role.
 *
 * @author Kamalakar Ponaka
 * @since 2.0
 */
@FunctionalInterface
public interface VaultAppRoleAccessStrategy {

	/**
	 * Return secrets from Vault. The response is represented as JSON object marshaled to
	 * {@link String}.
	 * @param requestEntity must not be {@literal null}.
	 * @return the marshaled JSON object or {@literal null} if the key was not found.
	 * @throws RestClientException in case of a transport/access failure.
	 * @see com.fasterxml.jackson.annotation.JsonRawValue
	 */
	String getAuth(HttpEntity<?> requestEntity) throws RestClientException;

	/**
	 * Vault App Role response POJO.
	 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	class VaultAppRoleResponse {

		private Object auth;

		@JsonProperty("lease_duration")
		private long leaseDuration;

		@JsonProperty("lease_id")
		private String leaseId;

		private boolean renewable;

		public VaultAppRoleResponse() {
		}

		public Object getAuth() {
			return this.auth;
		}

		public void setAuth(JsonNode auth) {
			this.auth = auth;
		}

		public long getLeaseDuration() {
			return this.leaseDuration;
		}

		public void setLeaseDuration(long leaseDuration) {
			this.leaseDuration = leaseDuration;
		}

		public String getLeaseId() {
			return this.leaseId;
		}

		public void setLeaseId(String leaseId) {
			this.leaseId = leaseId;
		}

		public boolean isRenewable() {
			return this.renewable;
		}

		public void setRenewable(boolean renewable) {
			this.renewable = renewable;
		}

	}

}
