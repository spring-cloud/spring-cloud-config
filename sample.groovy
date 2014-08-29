package demo

@Grab('org.springframework.cloud:spring-platform-config-client:1.0.0.BUILD-SNAPSHOT')
@Grab('spring-boot-starter-actuator')
@RestController
class Application {
  @RequestMapping("/")
  String home() {
    "Hello World"
  }
}