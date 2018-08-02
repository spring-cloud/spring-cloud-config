package org.springframework.cloud.config.server.support;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.security.GeneralSecurityException;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.cloud.config.server.environment.JGitEnvironmentProperties;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.isA;
import static org.junit.internal.matchers.ThrowableCauseMatcher.hasCause;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = HttpClientSupportTest.TestConfiguration.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class HttpClientSupportTest {

    @LocalServerPort
    private String localServerPort;

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void setsTimeout() throws GeneralSecurityException, IOException {
        JGitEnvironmentProperties properties = new JGitEnvironmentProperties();
        properties.setTimeout(1);
        CloseableHttpClient httpClient = HttpClientSupport.builder(properties).build();

        expectedException.expect(anyOf(isA(SocketTimeoutException.class), hasCause(isA(SocketTimeoutException.class))));

        httpClient.execute(new HttpGet(String.format("http://127.0.0.1:%s/test/endpoint", localServerPort)));
    }

    @SpringBootConfiguration
    @EnableWebMvc
    @EnableAutoConfiguration
    @RestController
    static class TestConfiguration {

        @GetMapping("/test/endpoint")
        public void testEndpoint() throws InterruptedException {
            Thread.sleep(2000);
        }
    }
}