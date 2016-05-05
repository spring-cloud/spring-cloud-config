package org.springframework.cloud.config.server.environment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;

import org.hibernate.validator.constraints.NotEmpty;
import org.hibernate.validator.constraints.Range;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.annotation.JsonProperty;

import static org.springframework.cloud.config.client.ConfigClientProperties.TOKEN_HEADER;

/**
 * @author Spencer Gibb
 */
@ConfigurationProperties("spring.cloud.config.server.vault")
public class VaultEnvironmentRepository implements EnvironmentRepository {

	public static final String VAULT_TOKEN = "X-Vault-Token";

	@NotEmpty
	private String host = "127.0.0.1";

	@Range(min = 1, max = 65535)
	private int port = 8200;

	private String scheme = "http";

	@NotEmpty
	private String backend = "secret";

	@NotEmpty
	private String defaultKey = "application";

	@NotEmpty
	private String profileSeparator = ",";

	private RestTemplate rest = new RestTemplate();

	//TODO: is there a better way to do this?
	@Autowired
	private HttpServletRequest request;

	@Override
	public Environment findOne(String application, String profile, String label) {
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);
		List<String> scrubbedProfiles = scrubProfiles(profiles);

		List<String> keys = findKeys(application, scrubbedProfiles);

		Environment environment = new Environment(application, profiles, label, null);

		for (String key : keys) {
			Map<String, String> data = read(key);
			if (data != null) {
				environment.add(new PropertySource("vault:"+key, data));
			}
		}

		return environment;
	}

	private List<String> findKeys(String application, List<String> profiles) {
		List<String> keys = new ArrayList<>();

		keys.add(this.defaultKey);
		addProfiles(keys, this.defaultKey, profiles);

		keys.add(application);
		addProfiles(keys, application, profiles);

		Collections.reverse(keys);
		return keys;
	}

	private List<String> scrubProfiles(String[] profiles) {
		List<String> scrubbedProfiles = new ArrayList<>(Arrays.asList(profiles));
		if (scrubbedProfiles.contains("default")) {
			scrubbedProfiles.remove("default");
		}
		return scrubbedProfiles;
	}

	private void addProfiles(List<String> contexts, String baseContext,
			List<String> profiles) {
		for (String profile : profiles) {
			contexts.add(baseContext + this.profileSeparator + profile);
		}
	}

	Map<String, String> read(String key) {
		String url = String.format("%s://%s:%s/v1/{backend}/{key}", this.scheme,
				this.host, this.port);

		HttpHeaders headers = new HttpHeaders();

		String token = request.getHeader(TOKEN_HEADER);
		if (!StringUtils.hasLength(token)) {
			throw new IllegalArgumentException("Missing required header: "+TOKEN_HEADER);
		}
		headers.add(VAULT_TOKEN, token);
		try {
			ResponseEntity<VaultResponse> response = this.rest.exchange(url,
					HttpMethod.GET, new HttpEntity<>(headers), VaultResponse.class,
					this.backend, key);

			HttpStatus status = response.getStatusCode();
			if (status == HttpStatus.OK) {
				return response.getBody().data;
			}
		}
		catch (HttpStatusCodeException e) {
			if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
				return null;
			}
			throw e;
		}

		return Collections.emptyMap();
	}

	static class VaultResponse {
		private String auth;

		private Map<String, String> data;

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

		public Map<String, String> getData() {
			return data;
		}

		public void setData(Map<String, String> data) {
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
