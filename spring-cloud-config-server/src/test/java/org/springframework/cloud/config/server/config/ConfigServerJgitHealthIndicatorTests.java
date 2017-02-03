/*
 * Copyright 2013-2014 the original author or authors.
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
package org.springframework.cloud.config.server.config;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Answers;
import org.mockito.Mock;
import org.springframework.boot.actuate.health.Status;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.server.environment.MultipleJGitEnvironmentRepository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * @author Ivan Vasyliev
 */
public class ConfigServerJgitHealthIndicatorTests {

    @Mock
    private MultipleJGitEnvironmentRepository repository;

    @Mock(answer = Answers.RETURNS_MOCKS)
    private Environment environment;

    private ConfigServerJgitHealthIndicator indicator;

    @Before
    public void init() {
        initMocks(this);
        indicator = new ConfigServerJgitHealthIndicator(repository, new ConfigServerProperties());
    }

    @Test
    public void defaultStatusWorks() {
        when(repository.findOne(anyString(), anyString(), anyString())).thenReturn(environment);
        assertEquals("wrong default status", Status.UP, indicator.health().getStatus());
    }

    @Test
    public void exceptionStatusIsDown() {
        when(repository.findOne(anyString(), anyString(), anyString())).thenThrow(new RuntimeException());
        assertEquals("wrong exception status", Status.DOWN, indicator.health().getStatus());
    }

    @Test
    public void passwordIsEncoded() {
        when(repository.findOne(anyString(), anyString(), anyString())).thenReturn(environment);
        when(repository.getPassword()).thenReturn("123456789");
        assertTrue("wrong password", indicator.health().toString().contains("123***"));

        when(repository.getPassword()).thenReturn("1");
        assertTrue("wrong password", indicator.health().toString().contains("******"));
    }

    @Test
    public void versionIsDisplayed() {
        when(repository.findOne(anyString(), anyString(), anyString())).thenReturn(environment);
        when(environment.getVersion()).thenReturn("1111111");
        assertTrue("wrong version", indicator.health().toString().contains("1111111"));
    }

    @Test
    public void stateIsDisplayed() {
        when(repository.findOne(anyString(), anyString(), anyString())).thenReturn(environment);
        when(environment.getDescription()).thenReturn("00000000");
        assertTrue("wrong description", indicator.health().toString().contains("00000000"));
    }
}
