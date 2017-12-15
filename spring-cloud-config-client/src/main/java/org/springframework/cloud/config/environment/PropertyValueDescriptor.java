package org.springframework.cloud.config.environment;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A description of a property's value, including its origin if available.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class PropertyValueDescriptor {

    private final Object value;

    private String origin;

    @JsonCreator
    public PropertyValueDescriptor(@JsonProperty("value") Object value,
                                    @JsonProperty("origin") String origin) {
        this.value = value;
        this.origin = origin;
    }

    public Object getValue() {
        return this.value;
    }

    public String getOrigin() {
        return this.origin;
    }

    public void setOrigin(String origin) {
        this.origin = origin;
    }
}
