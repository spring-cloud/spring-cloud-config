package org.springframework.cloud.config.server.environment;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriTemplateHandler;

/**
 * @author Spencer Gibb
 * @author Ryan Baxter
 * @author Mark Paluch
 */
public class VaultEnvironmentRepositoryTests {

	private ObjectMapper objectMapper = new ObjectMapper();

	@Before
	public void init() {}

	@Test
	public void testFindOne() throws IOException {
		MockHttpServletRequest configRequest = new MockHttpServletRequest();
		configRequest.addHeader("X-CONFIG-TOKEN", "mytoken");
		RestTemplate rest = Mockito.mock(RestTemplate.class);
		ResponseEntity<VaultEnvironmentRepository.VaultResponse> myAppResp = Mockito.mock(ResponseEntity.class);
		Mockito.when(rest.getUriTemplateHandler()).thenReturn(new DefaultUriTemplateHandler());
		Mockito.when(myAppResp.getStatusCode()).thenReturn(HttpStatus.OK);
		VaultEnvironmentRepository.VaultResponse myAppVaultResp = Mockito.mock(VaultEnvironmentRepository.VaultResponse.class);
		Mockito.when(myAppVaultResp.getData()).thenReturn(asJsonNode("{\"foo\":\"bar\"}"));
		Mockito.when(myAppResp.getBody()).thenReturn(myAppVaultResp);
		Mockito.when(rest.exchange(Mockito.eq(URI.create("http://127.0.0.1:8200/v1/secret/myapp")),
				Mockito.eq(HttpMethod.GET), Mockito.any(HttpEntity.class), Mockito.eq(VaultEnvironmentRepository.VaultResponse.class))).thenReturn(myAppResp);
		ResponseEntity<VaultEnvironmentRepository.VaultResponse> appResp = Mockito.mock(ResponseEntity.class);
		Mockito.when(appResp.getStatusCode()).thenReturn(HttpStatus.OK);
		VaultEnvironmentRepository.VaultResponse appVaultResp = Mockito.mock(VaultEnvironmentRepository.VaultResponse.class);
		Mockito.when(appVaultResp.getData()).thenReturn(null);
		Mockito.when(appResp.getBody()).thenReturn(appVaultResp);
		Mockito.when(rest.exchange(Mockito.eq(URI.create("http://127.0.0.1:8200/v1/secret/application")),
				Mockito.eq(HttpMethod.GET), Mockito.any(HttpEntity.class), Mockito.eq(VaultEnvironmentRepository.VaultResponse.class))).thenReturn(appResp);
		VaultEnvironmentRepository repo = new VaultEnvironmentRepository(configRequest, new EnvironmentWatch.Default(), rest);
		repo.afterPropertiesSet();
		Environment e = repo.findOne("myapp", null, null);
		assertEquals("myapp", e.getName());
		Map<String,String> result = new HashMap<String,String>();
		result.put("foo", "bar");
		assertEquals(result, e.getPropertySources().get(0).getSource());
	}

	@Test(expected = IllegalArgumentException.class)
	public void missingConfigToken() throws IOException {
		MockHttpServletRequest configRequest = new MockHttpServletRequest();
		RestTemplate rest = Mockito.mock(RestTemplate.class);
		ResponseEntity<VaultEnvironmentRepository.VaultResponse> myAppResp = Mockito.mock(ResponseEntity.class);
		Mockito.when(rest.getUriTemplateHandler()).thenReturn(new DefaultUriTemplateHandler());
		Mockito.when(myAppResp.getStatusCode()).thenReturn(HttpStatus.OK);
		VaultEnvironmentRepository.VaultResponse myAppVaultResp = Mockito.mock(VaultEnvironmentRepository.VaultResponse.class);
		Mockito.when(myAppVaultResp.getData()).thenReturn(asJsonNode("{\"foo\":\"bar\"}"));
		Mockito.when(myAppResp.getBody()).thenReturn(myAppVaultResp);
		Mockito.when(rest.exchange(Mockito.eq("http://127.0.0.1:8200/v1/{backend}/{key}"),
				Mockito.eq(HttpMethod.GET), Mockito.any(HttpEntity.class), Mockito.eq(VaultEnvironmentRepository.VaultResponse.class),
				Mockito.eq("secret"), Mockito.eq("myapp"))).thenReturn(myAppResp);
		VaultEnvironmentRepository repo = new VaultEnvironmentRepository(configRequest, new EnvironmentWatch.Default(), rest);
		repo.afterPropertiesSet();
		repo.findOne("myapp", null, null);
	}

	private JsonNode asJsonNode(String content) {
		try {
			return objectMapper.readTree(content);
		} catch (IOException e) {
			throw new IllegalArgumentException(e);
		}
	}
}
