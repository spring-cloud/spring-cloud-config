package org.springframework.cloud.config.server.environment;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.ObjectProvider;
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
	public void init() {
	}

	@SuppressWarnings("unchecked")
	private ObjectProvider<HttpServletRequest> mockProvide(HttpServletRequest request) {
		ObjectProvider<HttpServletRequest> objectProvider = mock(ObjectProvider.class);
		when(objectProvider.getIfAvailable()).thenReturn(request);
		return objectProvider;
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testFindOneNoDefaultKey() {
		MockHttpServletRequest configRequest = new MockHttpServletRequest();
		configRequest.addHeader("X-CONFIG-TOKEN", "mytoken");
		RestTemplate rest = mock(RestTemplate.class);

		ResponseEntity<VaultEnvironmentRepository.VaultResponse> myAppResp = mock(ResponseEntity.class);
		when(myAppResp.getStatusCode()).thenReturn(HttpStatus.OK);
		VaultEnvironmentRepository.VaultResponse myAppVaultResp = mock(VaultEnvironmentRepository.VaultResponse.class);
		when(myAppVaultResp.getData()).thenReturn("{\"foo\":\"bar\"}");
		when(myAppResp.getBody()).thenReturn(myAppVaultResp);
		when(rest.exchange(eq("http://127.0.0.1:8200/v1/{backend}/{key}"),
			eq(HttpMethod.GET), any(HttpEntity.class),
			eq(VaultEnvironmentRepository.VaultResponse.class),
			eq("secret"), eq("myapp"))).thenReturn(myAppResp);

		ResponseEntity<VaultEnvironmentRepository.VaultResponse> appResp = mock(ResponseEntity.class);
		when(appResp.getStatusCode()).thenReturn(HttpStatus.OK);
		VaultEnvironmentRepository.VaultResponse appVaultResp = mock(VaultEnvironmentRepository.VaultResponse.class);
		when(appVaultResp.getData()).thenReturn("{\"def-foo\":\"def-bar\"}");
		when(appResp.getBody()).thenReturn(appVaultResp);
		when(rest.exchange(eq("http://127.0.0.1:8200/v1/{backend}/{key}"),
			eq(HttpMethod.GET), any(HttpEntity.class),
			eq(VaultEnvironmentRepository.VaultResponse.class),
			eq("secret"), eq("application"))).thenReturn(appResp);

		VaultEnvironmentRepository repo = new VaultEnvironmentRepository(mockProvide(configRequest),
			new EnvironmentWatch.Default(), rest);

		Environment e = repo.findOne("myapp", null, null);
		assertEquals("Name should be the same as the application argument", "myapp", e.getName());
		assertEquals(
			"Properties for specified application and default application with key 'application' should be returned", 2,
			e.getPropertySources().size());
		Map<String, String> firstResult = new HashMap<String, String>();
		firstResult.put("foo", "bar");
		assertEquals("Properties for specified application should be returned in priority position", firstResult,
			e.getPropertySources().get(0).getSource());

		Map<String, String> secondResult = new HashMap<String, String>();
		secondResult.put("def-foo", "def-bar");
		assertEquals("Properties for default application with key 'application' should be returned in second position",
			secondResult, e.getPropertySources().get(1).getSource());
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testFindOneDefaultKeySetAndDifferentToApplication() {
		MockHttpServletRequest configRequest = new MockHttpServletRequest();
		configRequest.addHeader("X-CONFIG-TOKEN", "mytoken");
		RestTemplate rest = mock(RestTemplate.class);

		ResponseEntity<VaultEnvironmentRepository.VaultResponse> myAppResp = mock(ResponseEntity.class);
		when(myAppResp.getStatusCode()).thenReturn(HttpStatus.OK);
		VaultEnvironmentRepository.VaultResponse myAppVaultResp = mock(VaultEnvironmentRepository.VaultResponse.class);
		when(myAppVaultResp.getData()).thenReturn("{\"foo\":\"bar\"}");
		when(myAppResp.getBody()).thenReturn(myAppVaultResp);
		when(rest.exchange(eq("http://127.0.0.1:8200/v1/{backend}/{key}"),
			eq(HttpMethod.GET), any(HttpEntity.class),
			eq(VaultEnvironmentRepository.VaultResponse.class),
			eq("secret"), eq("myapp"))).thenReturn(myAppResp);

		ResponseEntity<VaultEnvironmentRepository.VaultResponse> myDefaultKeyResp = mock(ResponseEntity.class);
		when(myDefaultKeyResp.getStatusCode()).thenReturn(HttpStatus.OK);
		VaultEnvironmentRepository.VaultResponse myDefaultKeyVaultResp = mock(
			VaultEnvironmentRepository.VaultResponse.class);
		when(myDefaultKeyVaultResp.getData()).thenReturn("{\"def-foo\":\"def-bar\"}");
		when(myDefaultKeyResp.getBody()).thenReturn(myDefaultKeyVaultResp);
		when(rest.exchange(eq("http://127.0.0.1:8200/v1/{backend}/{key}"),
			eq(HttpMethod.GET), any(HttpEntity.class),
			eq(VaultEnvironmentRepository.VaultResponse.class),
			eq("secret"), eq("mydefaultkey"))).thenReturn(myDefaultKeyResp);

		VaultEnvironmentRepository repo = new VaultEnvironmentRepository(mockProvide(configRequest),
			new EnvironmentWatch.Default(),
			rest);
		repo.setDefaultKey("mydefaultkey");

		Environment e = repo.findOne("myapp", null, null);
		assertEquals("Name should be the same as the application argument", "myapp", e.getName());
		assertEquals(
			"Properties for specified application and default application with key 'mydefaultkey' should be returned",
			2, e.getPropertySources().size());

		Map<String, String> firstResult = new HashMap<String, String>();
		firstResult.put("foo", "bar");
		assertEquals("Properties for specified application should be returned in priority position", firstResult,
			e.getPropertySources().get(0).getSource());

		Map<String, String> secondResult = new HashMap<String, String>();
		secondResult.put("def-foo", "def-bar");
		assertEquals("Properties for default application with key 'mydefaultkey' should be returned in second position",
			secondResult, e.getPropertySources().get(1).getSource());
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testFindOneDefaultKeySetAndEqualToApplication() {
		MockHttpServletRequest configRequest = new MockHttpServletRequest();
		configRequest.addHeader("X-CONFIG-TOKEN", "mytoken");
		RestTemplate rest = mock(RestTemplate.class);

		ResponseEntity<VaultEnvironmentRepository.VaultResponse> myAppResp = mock(ResponseEntity.class);
		when(myAppResp.getStatusCode()).thenReturn(HttpStatus.OK);
		VaultEnvironmentRepository.VaultResponse myAppVaultResp = mock(VaultEnvironmentRepository.VaultResponse.class);
		when(myAppVaultResp.getData()).thenReturn("{\"foo\":\"bar\"}");
		when(myAppResp.getBody()).thenReturn(myAppVaultResp);
		when(rest.exchange(eq("http://127.0.0.1:8200/v1/{backend}/{key}"),
			eq(HttpMethod.GET), any(HttpEntity.class),
			eq(VaultEnvironmentRepository.VaultResponse.class),
			eq("secret"), eq("myapp"))).thenReturn(myAppResp);

		ResponseEntity<VaultEnvironmentRepository.VaultResponse> appResp = mock(ResponseEntity.class);
		when(appResp.getStatusCode()).thenReturn(HttpStatus.OK);
		VaultEnvironmentRepository.VaultResponse appVaultResp = mock(VaultEnvironmentRepository.VaultResponse.class);
		when(appVaultResp.getData()).thenReturn("{\"def-foo\":\"def-bar\"}");
		when(appResp.getBody()).thenReturn(appVaultResp);
		when(rest.exchange(eq("http://127.0.0.1:8200/v1/{backend}/{key}"),
			eq(HttpMethod.GET), any(HttpEntity.class),
			eq(VaultEnvironmentRepository.VaultResponse.class),
			eq("secret"), eq("application"))).thenReturn(appResp);

		VaultEnvironmentRepository repo = new VaultEnvironmentRepository(mockProvide(configRequest),
			new EnvironmentWatch.Default(),
			rest);
		repo.setDefaultKey("myapp");

		Environment e = repo.findOne("myapp", null, null);
		assertEquals("Name should be the same as the application argument", "myapp", e.getName());
		assertEquals("Only properties for specified application should be returned", 1, e.getPropertySources().size());

		Map<String, String> result = new HashMap<String, String>();
		result.put("foo", "bar");
		assertEquals("Properties should be returned for specified application", result,
			e.getPropertySources().get(0).getSource());
	}

	@Test(expected = IllegalArgumentException.class)
	@SuppressWarnings("unchecked")
	public void missingConfigToken() {
		MockHttpServletRequest configRequest = new MockHttpServletRequest();
		RestTemplate rest = mock(RestTemplate.class);
		ResponseEntity<VaultEnvironmentRepository.VaultResponse> myAppResp = mock(ResponseEntity.class);
		when(myAppResp.getStatusCode()).thenReturn(HttpStatus.OK);
		VaultEnvironmentRepository.VaultResponse myAppVaultResp = mock(VaultEnvironmentRepository.VaultResponse.class);
		when(myAppVaultResp.getData()).thenReturn("{\"foo\":\"bar\"}");
		when(myAppResp.getBody()).thenReturn(myAppVaultResp);
		when(rest.exchange(eq("http://127.0.0.1:8200/v1/{backend}/{key}"),
			eq(HttpMethod.GET), any(HttpEntity.class),
			eq(VaultEnvironmentRepository.VaultResponse.class),
			eq("secret"), eq("myapp"))).thenReturn(myAppResp);
		VaultEnvironmentRepository repo = new VaultEnvironmentRepository(mockProvide(configRequest),
			new EnvironmentWatch.Default(),
			rest);
		repo.findOne("myapp", null, null);
	}

	@Test(expected = IllegalStateException.class)
	@SuppressWarnings("unchecked")
	public void missingHttpRequest() {
		ObjectProvider<HttpServletRequest> objectProvider = mock(ObjectProvider.class);
		when(objectProvider.getIfAvailable()).thenReturn(null);

		RestTemplate rest = mock(RestTemplate.class);
		VaultEnvironmentRepository repo = new VaultEnvironmentRepository(objectProvider,
			new EnvironmentWatch.Default(), rest);
		repo.findOne("myapp", null, null);
	}
}
