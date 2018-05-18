package org.springframework.cloud.config.server.environment;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ByteArrayResource;

import java.io.IOException;
import java.util.Properties;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;

/**
 * @author Haroun Pacquee
 */
public class VaultResponseTest {

    private ObjectMapper objectMapper;

    @Before
    public void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    public void testGetDataNonVersioned() {
        String json = "{\n" +
                "    \"request_id\": \"0c53d4ef-e5ca-22b3-cda4-1c9fb80adfce\",\n" +
                "    \"lease_id\": \"\",\n" +
                "    \"renewable\": false,\n" +
                "    \"lease_duration\": 2764800,\n" +
                "    \"data\": {\n" +
                "        \"foo\": \"bar\"\n" +
                "    },\n" +
                "    \"wrap_info\": null,\n" +
                "    \"warnings\": null,\n" +
                "    \"auth\": null\n" +
                "}";

        final VaultEnvironmentRepository.VaultResponse vaultResponse;
        try {
            vaultResponse = objectMapper.readValue(json, VaultEnvironmentRepository.VaultResponse.class);

            final YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
            yaml.setResources(new ByteArrayResource(vaultResponse.getData().getBytes()));
            Properties properties = yaml.getObject();
            assertNotNull(properties.getProperty("foo"));
            assertThat(properties.getProperty("foo"), is("bar"));
        } catch (IOException e) {
            fail();
        }
    }

    @Test
    public void testGetDataVersioned() {

        String json = "{\n" +
                "    \"request_id\": \"69831ded-2815-a928-8aef-4b62b5308546\",\n" +
                "    \"lease_id\": \"\",\n" +
                "    \"renewable\": false,\n" +
                "    \"lease_duration\": 0,\n" +
                "    \"data\": {\n" +
                "        \"data\": {\n" +
                "            \"foo\": \"bar\"\n" +
                "        },\n" +
                "        \"metadata\": {\n" +
                "            \"created_time\": \"2018-05-18T15:22:59.733912Z\",\n" +
                "            \"deletion_time\": \"\",\n" +
                "            \"destroyed\": false,\n" +
                "            \"version\": 1\n" +
                "        }\n" +
                "    },\n" +
                "    \"wrap_info\": null,\n" +
                "    \"warnings\": null,\n" +
                "    \"auth\": null\n" +
                "}";

        try {
            final VaultEnvironmentRepository.VersionedVaultResponse versionedVaultResponse = objectMapper.readValue(json, VaultEnvironmentRepository.VersionedVaultResponse.class);

            final YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
            yaml.setResources(new ByteArrayResource(versionedVaultResponse.getData().getBytes()));
            Properties properties = yaml.getObject();
            assertNotNull(properties.getProperty("foo"));
            assertThat(properties.getProperty("foo"), is("bar"));
        } catch (IOException e) {
            fail();
        }
    }

}