Spring Platform Config provides server and client-side support for
externalized configuration in a distributed system. With the Config
Server you have a central place to manage external properties for the
applications across all environments. The concepts on both sides map
identically to the Spring `Environment` and `PropertySource`
abstractions, so they fit very well with Spring applications. As an
application moves through the deployment pipeline from dev to test and
into production you can manage the configuration that needs to change
between those environments and be certain that applications have
everything they need to run when they migrate. The default
implementation of the server repository strategy uses git as a storage
backend so it easily supports labelled versions of configurations.

## Quick Start

Start the server:

```
$ cd spring-platform-config-server
$ mvn spring-boot:run
```

The server is a Spring Boot application so you can build the jar file
and run that (`java -jar ...`) or pull it down from a Maven
repository. Then try it out as a client:

```
$ curl localhost:8888/foo/development
{"name":"development","label":"master","propertySources":[
  {"name":"https://github.com/scratches/config-repo/foo-development.properties","source":{"bar":"spam"}},
  {"name":"https://github.com/scratches/config-repo/foo.properties","source":{"foo":"bar"}}
]}
```

The default strategy for locating property sources is to clone a git
repository (at "spring.platform.config.server.uri") and use it to initialize
a `SpringApplication`. The application's `Environment` is used to
enumerate property sources. The service has resources in the form:

```
/{name}/{profile}[/{label}]
```

where the "name" is used as the config name in the `SpringApplication`
(i.e. what is normally "application" in a regular Spring Boot app),
"profile" is an active profile (or comma-separated list of
properties), and "label" is an optional git label (defaults to
"master").

### Client Side Usage

Build a client (e.g. see the test cases for the config-client) as a
Spring Boot application that depends on spring-platform-config-client.
When it runs it will pick up the external configuration from the
default local config server on port 8888 if it is running. To modify
the startup behaviour you can change the location of the server using
`bootstrap.properties` (like `application.properties` but for the
bootstrap phase of an application context), e.g.

```
spring.platform.config.uri: http://myconfigserver.com
```

## Sample Application

There is a sample application
[here](https://github.com/spring-platform/spring-platform-config/spring-platform-config-sample). It
is a Spring Boot application so you can run it using the usual
mechanisms (for instance "mvn spring-boot:run"). When it runs it will
look for the config server on "http://localhost:8888" by default, so
you could run the server as well to see it all working together.

The sample has a tets case where the config server is also started in
the same JVM (with a different port), and the test asserts that an
environment property from the git configuration repo is present.
