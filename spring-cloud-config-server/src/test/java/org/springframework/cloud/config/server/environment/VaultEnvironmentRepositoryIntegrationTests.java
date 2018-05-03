/*
 * Copyright 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.cloud.config.server.environment;

import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.servlet.http.HttpServletRequest;

import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContextBuilder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * author Dylan Roberts
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = VaultEnvironmentRepositoryIntegrationTests.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "server.ssl.key-store=classpath:ssl-test.jks",
            "server.ssl.key-store-password=password",
            "server.ssl.key-password=password",
            "server.key-alias=ssl-test"})
public class VaultEnvironmentRepositoryIntegrationTests {

    @LocalServerPort
    private String localServerPort;

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void withSslValidation() {
        VaultEnvironmentRepositoryFactory vaultEnvironmentRepositoryFactory =
                new VaultEnvironmentRepositoryFactory(withRequest(), new EnvironmentWatch.Default(),
                        Optional.of(skipSslValidationRestTemplate()));
        VaultEnvironmentRepository vaultEnvironmentRepository =
                vaultEnvironmentRepositoryFactory.build(withEnvironmentProperties(false));
        expectedException.expectCause(instanceOf(SSLHandshakeException.class));

        vaultEnvironmentRepository.findOne("application", "profile", "label");
    }

    @Test
    public void skipSslValidation() {
        VaultEnvironmentRepositoryFactory vaultEnvironmentRepositoryFactory =
                new VaultEnvironmentRepositoryFactory(withRequest(), new EnvironmentWatch.Default(),
                        Optional.of(skipSslValidationRestTemplate()));
        VaultEnvironmentRepository vaultEnvironmentRepository =
                vaultEnvironmentRepositoryFactory.build(withEnvironmentProperties(true));

        Environment actual = vaultEnvironmentRepository.findOne("application", "profile", "label");

        assertThat(actual).isNotNull();
    }

    private RestTemplate skipSslValidationRestTemplate() {
        try {
            SSLContext sslContext = new SSLContextBuilder()
                    .loadTrustMaterial(null, (certificate, authType) -> true)
                    .build();
            CloseableHttpClient httpClient = HttpClients.custom()
                    .setSSLContext(sslContext)
                    .setSSLHostnameVerifier(new NoopHostnameVerifier())
                    .build();
            HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory();
            requestFactory.setHttpClient(httpClient);
            return new RestTemplate(requestFactory);
        } catch (NoSuchAlgorithmException | KeyStoreException | KeyManagementException e) {
            throw new RuntimeException(e);
        }
    }

    private VaultEnvironmentProperties withEnvironmentProperties(boolean skipSslValidation) {
        VaultEnvironmentProperties environmentProperties = new VaultEnvironmentProperties();
        environmentProperties.setPort(Integer.decode(localServerPort));
        environmentProperties.setScheme("https");
        environmentProperties.setSkipSslValidation(skipSslValidation);
        return environmentProperties;
    }

    private ObjectProvider<HttpServletRequest> withRequest() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Config-Token")).thenReturn("configToken");
        ObjectProvider<HttpServletRequest> requestProvider = mock(ObjectProvider.class);
        when(requestProvider.getIfAvailable()).thenReturn(request);
        return requestProvider;
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    public static class TestApplication {

        public static void main(String[] args) {
            SpringApplication.run(TestApplication.class, args);
        }
    }
}
