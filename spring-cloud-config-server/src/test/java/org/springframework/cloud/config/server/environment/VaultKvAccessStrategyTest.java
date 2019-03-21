/*
 * Copyright 2018 the original author or authors.
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

import java.io.IOException;
import java.lang.reflect.UndeclaredThrowableException;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import org.springframework.cloud.config.server.environment.VaultKvAccessStrategy.VaultResponse;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

/**
 * @author Haroun Pacquee
 */
public class VaultKvAccessStrategyTest {

	private ObjectMapper objectMapper = new ObjectMapper();

	private static final String FOO_BAR = "{\"foo\":\"bar\"}";

	@Test
	public void testV1ExtractFromBody() {
		String json = "{\"data\": {\"foo\": \"bar\"}}";
		String s = getStrategy(1).extractDataFromBody(getVaultResponse(json));
		assertThat(s, is(FOO_BAR));
	}

	@Test
	public void testV1ExtractFromBodyNoData() {
		String json = "{}";
		String s = getStrategy(1).extractDataFromBody(getVaultResponse(json));
		assertNull(s);
	}

	@Test
	public void testV2ExtractFromBody() {
		String json = "{\"data\": {\"data\": {\"foo\": \"bar\"}}}";
		String s = getStrategy(2).extractDataFromBody(getVaultResponse(json));
		assertThat(s, is(FOO_BAR));
	}

	@Test
	public void testV2ExtractFromBodyEmptyNestedData() {
		String json = "{\"data\": {}}";
		String s = getStrategy(2).extractDataFromBody(getVaultResponse(json));
		assertNull(s);
	}

	@Test
	public void testV2ExtractFromBodyNoData() {
		String json = "{}";
		String s = getStrategy(2).extractDataFromBody(getVaultResponse(json));
		assertNull(s);
	}

	private VaultResponse getVaultResponse(String json) {
		try {
			return objectMapper.readValue(json, VaultResponse.class);
		}
		catch (IOException e) {
			throw new UndeclaredThrowableException(e);
		}
	}

	private static VaultKvAccessStrategySupport getStrategy(int version) {
		return (VaultKvAccessStrategySupport) VaultKvAccessStrategyFactory
				.forVersion(null, "foo", version);
	}
}
