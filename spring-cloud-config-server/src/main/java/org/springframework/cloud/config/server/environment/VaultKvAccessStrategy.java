package org.springframework.cloud.config.server.environment;

import com.fasterxml.jackson.annotation.JsonRawValue;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestOperations;

public interface VaultKvAccessStrategy {

    String getPath();

    String extractDataFromBody(VaultEnvironmentRepository.VaultResponse body);

    default String getData(RestOperations rest, String baseUrl, HttpHeaders headers, String backend, String key) {
        try {
            final ResponseEntity<VaultEnvironmentRepository.VaultResponse> response = rest.exchange(baseUrl + getPath(), HttpMethod.GET, new HttpEntity<>(headers), VaultEnvironmentRepository.VaultResponse.class, backend, key);
            HttpStatus status = response.getStatusCode();
            if (status == HttpStatus.OK) {
                return extractDataFromBody(response.getBody());
            }
        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                return null;
            }
            throw e;
        }
        return null;
    }
}

class V1VaultKvAccessStrategy implements  VaultKvAccessStrategy {

    @Override
    public String getPath() {
        return "/v1/{backend}/{key}";
    }

    @JsonRawValue
    @Override
    public String extractDataFromBody(VaultEnvironmentRepository.VaultResponse body) {
        return body.getData() == null ? null : body.getData().toString();
    }
}

class V2VaultKvAccessStrategy implements VaultKvAccessStrategy {

    @Override
    public String getPath() {
        return "/v1/{backend}/data/{key}";
    }

    @JsonRawValue
    @Override
    public String extractDataFromBody(VaultEnvironmentRepository.VaultResponse body) {
        final JsonNode nestedDataNode = body.getData() == null ? null : ((JsonNode) body.getData()).get("data");
        return nestedDataNode == null ? null : nestedDataNode.toString();
    }


}


