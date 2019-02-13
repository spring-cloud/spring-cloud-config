package demo

@Grab('spring-cloud-starter')
@RestController
class Application {
	@RequestMapping("/")
	String home() {
		"Hello World"
	}
}
