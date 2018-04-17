package org.springframework.cloud.config.server.support;

import static org.junit.Assert.assertEquals;
import static org.springframework.cloud.config.server.support.EnvironmentPropertySource.resolvePlaceholders;

import org.junit.Test;
import org.springframework.core.env.StandardEnvironment;

public class EnvironmentPropertySourceTest {
  private final StandardEnvironment env = new StandardEnvironment();
  
  @Test
  public void testEscapedPlaceholdersRemoved() {
    assertEquals("${abc}", resolvePlaceholders(env, "\\${abc}"));
    // JSON generated from jackson will be double escaped
    assertEquals("${abc}", resolvePlaceholders(env, "\\\\${abc}"));
  }
}
