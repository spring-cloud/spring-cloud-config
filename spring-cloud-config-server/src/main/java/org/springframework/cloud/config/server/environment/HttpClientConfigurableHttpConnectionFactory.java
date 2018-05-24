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

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.Map;

import org.apache.http.impl.client.HttpClientBuilder;
import org.eclipse.jgit.transport.http.HttpConnection;
import org.eclipse.jgit.transport.http.apache.HttpClientConnection;

import org.springframework.cloud.config.server.support.HttpClientSupport;

/**
 * @author Dylan Roberts
 */
public class HttpClientConfigurableHttpConnectionFactory implements ConfigurableHttpConnectionFactory {

    private Map<String, HttpClientBuilder> httpClientsByUri = new HashMap<>();

    @Override
    public void addConfiguration(MultipleJGitEnvironmentProperties environmentProperties)
            throws GeneralSecurityException {
        addHttpClient(environmentProperties);
        for (JGitEnvironmentProperties repo : environmentProperties.getRepos().values()) {
            addHttpClient(repo);
        }
    }

    @Override
    public HttpConnection create(URL url) throws IOException {
        return create(url, null);
    }

    @Override
    public HttpConnection create(URL url, Proxy proxy) throws IOException {
        return new HttpClientConnection(url.toString(), proxy, lookupHttpClientBuilder(url).build());
    }

    private void addHttpClient(JGitEnvironmentProperties properties) throws GeneralSecurityException {
        if (properties.getUri().startsWith("http")) {
            httpClientsByUri.put(properties.getUri(), HttpClientSupport.builder(properties));
        }
    }

    private HttpClientBuilder lookupHttpClientBuilder(URL url) throws MalformedURLException {
        String spec = url.toString();
        HttpClientBuilder builder = httpClientsByUri.get(spec);
        while (builder == null) {
            spec = spec.substring(0, spec.lastIndexOf(File.separator));
            builder = httpClientsByUri.get(spec);
        }
        return builder;
    }
}
