package org.springframework.cloud.bootstrap.encrypt;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.autoconfigure.ConfigClientAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

import static org.junit.Assert.assertEquals;

public class EnvironmentDecryptTests {

    private static ConfigurableApplicationContext context;

    @BeforeClass
    public static void setupSpec() {
        System.setProperty("encrypt.key", "eKey");
        context = new SpringApplicationBuilder(EnvironmentDecryptTestApp.class, TestConfigurationWithPropertySource.class)
                .web(false)
                .showBanner(false)
                .properties("enc.prop:{cipher}f43b8323cd82a74aafa1fba5efdce529274b58f68145903e6cc7e460e07e0e20")
                .run("--spring.config.name=decryptingPropertyTest");
    }

    @AfterClass
    public static void afterSpec() {
        System.getProperties().remove("encrypt.key");
        if (context != null) {
            context.close();
        }
    }

    @Test
    public void shouldDecryptPropertiesFromApplicationPropertiesFile() {
        assertEquals("enc.application.prop.value", context.getEnvironment().getProperty("enc.application.prop"));
    }

    @Test
    public void shouldDecryptPropertiesSetOnEnvironment() {
        assertEquals("enc.prop.value", context.getEnvironment().getProperty("enc.prop"));
    }

    @Ignore("Currently EnvironmentDecryptApplicationListener is run to early")
    @Test
    public void shouldDecryptPropertiesAddedWithPropertySourceAnnotation() {
        assertEquals("enc.propertySource.prop.value", context.getEnvironment().getProperty("enc.propertySource.prop"));
    }
}

@Configuration
@EnableAutoConfiguration
@ComponentScan(basePackageClasses = ConfigClientAutoConfiguration.class)
class EnvironmentDecryptTestApp {
}

@Configuration
@PropertySource("testConfigurationWithPropertySource.properties")
class TestConfigurationWithPropertySource {
}
