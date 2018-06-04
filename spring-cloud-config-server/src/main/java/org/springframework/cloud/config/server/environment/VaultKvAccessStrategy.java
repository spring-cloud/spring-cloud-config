package org.springframework.cloud.config.server.environment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClientException;

/**
 * Strategy interface to obtain secrets from Vault's key-value secret backend.
 * @author Haroun Pacquee
 * @author Mark Paluch
 * @since 2.0
 */
@FunctionalInterface
public interface VaultKvAccessStrategy {

	/**
	 * Return secrets from Vault. The response is represented as JSON object marshaled to
	 * {@link String}.
	 * @param headers must not be {@literal null}.
	 * @param backend secret backend mount path, must not be {@literal null}.
	 * @param key key within the key-value secret backend, must not be {@literal null}.
	 * @return the marshaled JSON object or {@literal null} if the key was not found.
	 * @throws RestClientException in case of a transport/access failure.
	 * @see com.fasterxml.jackson.annotation.JsonRawValue
	 */
	String getData(HttpHeaders headers, String backend, String key)
			throws RestClientException;

	@JsonIgnoreProperties(ignoreUnknown = true)
	class VaultResponse {

		private String auth;

		private Object data;

		@JsonProperty("lease_duration")
		private long leaseDuration;

		@JsonProperty("lease_id")
		private String leaseId;

		private boolean renewable;

		public VaultResponse() {
		}

		public String getAuth() {
			return auth;
		}

		public void setAuth(String auth) {
			this.auth = auth;
		}

		public Object getData() {
			return data;
		}

		public void setData(JsonNode data) {
			this.data = data;
		}

		public long getLeaseDuration() {
			return leaseDuration;
		}

		public void setLeaseDuration(long leaseDuration) {
			this.leaseDuration = leaseDuration;
		}

		public String getLeaseId() {
			return leaseId;
		}

		public void setLeaseId(String leaseId) {
			this.leaseId = leaseId;
		}

		public boolean isRenewable() {
			return renewable;
		}

		public void setRenewable(boolean renewable) {
			this.renewable = renewable;
		}
	}
}
