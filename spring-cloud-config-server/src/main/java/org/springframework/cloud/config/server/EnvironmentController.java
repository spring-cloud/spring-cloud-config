
package org.springframework.cloud.config.server;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.config.Environment;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EnvironmentController {

	private EnvironmentRepository repository;
	
	private EncryptionController encryption;

	@Autowired
	public EnvironmentController(EnvironmentRepository repository,
			EncryptionController encryption) {
		super();
		this.repository = repository;
		this.encryption = encryption;
	}

	@RequestMapping("/{name}/{env}")
	public Environment master(@PathVariable String name, @PathVariable String env) {
		return properties(name, env, "master");
	}

	@RequestMapping("/{name}/{env}/{label}")
	public Environment properties(@PathVariable String name, @PathVariable String env, @PathVariable String label) {	
		return encryption.decrypt(repository.findOne(name, env, label));
	}

}
