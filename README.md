Spring Platform Config provides server and client-side support for
externalized configuration in a distributed system. With the Config
Server you have a central place to manage external properties for the
applications in your origanization, across all environments. The
concepts on both sides map identically to the Spring `Environment` and
`PropertySource` abstractions, so they fit very well with Spring
applications. As an application moves through the deployment pipeline
from dev to test and into production you can manage the configuration
that needs to change between those environments and be certain that
applications have everything they need to run when they migrate.

## Quick Start

Start the server:

```
$ cd spring-platform-config-server
$ mvn spring-boot:run
```

Build a client (e.g. see the test cases for the config-client) as a
Spring Boot application that depends on spring-platform-config-client.
When it runs it will pick up the external configuration from the
default local config server on port 8888. To modify the startup
behaviour change the location of the server using
`bootstrap.properties` (like `application.properties` but for the
bootstrap phase of an application context), e.g.

```
spring.platform.config.url: http://myconfigserver.com
```

TODO: add a sample app.
