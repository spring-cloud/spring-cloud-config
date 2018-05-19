package org.springframework.cloud.config.server.environment;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.junit.Assert.*;

public class VaultKvAccessStrategyTest {

    private ObjectMapper objectMapper;

    private static final String FOO_BAR = "{\"foo\":\"bar\"}";

    @Before
    public void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    public void testV1ExtractFromBody() {
        String json = "{\"data\": {\"foo\": \"bar\"}}";

        try {
            final String s = new V1VaultKvAccessStrategy().extractDataFromBody(getVaultResponse(json));
            assertThat(s, is(FOO_BAR));
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void testV1ExtractFromBodyNoData() {
        String json = "{}";

        try {
            final String s = new V1VaultKvAccessStrategy().extractDataFromBody(getVaultResponse(json));
            assertNull(s);
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void testV2ExtractFromBody() {
        String json = "{\"data\": {\"data\": {\"foo\": \"bar\"}}}";

        try {
            final String s = new V2VaultKvAccessStrategy().extractDataFromBody(getVaultResponse(json));
            assertThat(s, is(FOO_BAR));
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void testV2ExtractFromBodyEmptyNestedData() {
        String json = "{\"data\": {}}";

        try {
            final String s = new V2VaultKvAccessStrategy().extractDataFromBody(getVaultResponse(json));
            assertNull(s);
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void testV2ExtractFromBodyNoData() {
        String json = "{}";

        try {
            final String s = new V2VaultKvAccessStrategy().extractDataFromBody(getVaultResponse(json));
            assertNull(s);
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    private VaultEnvironmentRepository.VaultResponse getVaultResponse(String json) throws java.io.IOException {
        return objectMapper.readValue(json, VaultEnvironmentRepository.VaultResponse.class);
    }


}