package org.springframework.cloud.config.server.environment;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

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

/**
 * @author Spencer Gibb
 * @author Ryan Baxter
 */
public class VaultEnvironmentRepositoryTests {

	@Before
	public void init() {}

	@Test
	public void testFindOne() throws IOException {
		MockHttpServletRequest configRequest = new MockHttpServletRequest();
		configRequest.addHeader("X-CONFIG-TOKEN", "mytoken");
		RestTemplate rest = Mockito.mock(RestTemplate.class);
		ResponseEntity<VaultEnvironmentRepository.VaultResponse> myAppResp = Mockito.mock(ResponseEntity.class);
		Mockito.when(myAppResp.getStatusCode()).thenReturn(HttpStatus.OK);
		VaultEnvironmentRepository.VaultResponse myAppVaultResp = Mockito.mock(VaultEnvironmentRepository.VaultResponse.class);
		Mockito.when(myAppVaultResp.getData()).thenReturn("{\"foo\":\"bar\"}");
		Mockito.when(myAppResp.getBody()).thenReturn(myAppVaultResp);
		Mockito.when(rest.exchange(Mockito.eq("http://127.0.0.1:8200/v1/{backend}/{key}"),
				Mockito.eq(HttpMethod.GET), Mockito.any(HttpEntity.class), Mockito.eq(VaultEnvironmentRepository.VaultResponse.class),
				Mockito.eq("secret"), Mockito.eq("myapp"))).thenReturn(myAppResp);
		ResponseEntity<VaultEnvironmentRepository.VaultResponse> appResp = Mockito.mock(ResponseEntity.class);
		Mockito.when(appResp.getStatusCode()).thenReturn(HttpStatus.OK);
		VaultEnvironmentRepository.VaultResponse appVaultResp = Mockito.mock(VaultEnvironmentRepository.VaultResponse.class);
		Mockito.when(appVaultResp.getData()).thenReturn(null);
		Mockito.when(appResp.getBody()).thenReturn(appVaultResp);
		Mockito.when(rest.exchange(Mockito.eq("http://127.0.0.1:8200/v1/{backend}/{key}"),
				Mockito.eq(HttpMethod.GET), Mockito.any(HttpEntity.class), Mockito.eq(VaultEnvironmentRepository.VaultResponse.class),
				Mockito.eq("secret"), Mockito.eq("application"))).thenReturn(appResp);
		VaultEnvironmentRepository repo = new VaultEnvironmentRepository(configRequest, new EnvironmentWatch.Default(), rest);
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
		Mockito.when(myAppResp.getStatusCode()).thenReturn(HttpStatus.OK);
		VaultEnvironmentRepository.VaultResponse myAppVaultResp = Mockito.mock(VaultEnvironmentRepository.VaultResponse.class);
		Mockito.when(myAppVaultResp.getData()).thenReturn("{\"foo\":\"bar\"}");
		Mockito.when(myAppResp.getBody()).thenReturn(myAppVaultResp);
		Mockito.when(rest.exchange(Mockito.eq("http://127.0.0.1:8200/v1/{backend}/{key}"),
				Mockito.eq(HttpMethod.GET), Mockito.any(HttpEntity.class), Mockito.eq(VaultEnvironmentRepository.VaultResponse.class),
				Mockito.eq("secret"), Mockito.eq("myapp"))).thenReturn(myAppResp);
		VaultEnvironmentRepository repo = new VaultEnvironmentRepository(configRequest, new EnvironmentWatch.Default(), rest);
		repo.findOne("myapp", null, null);
	}
}
