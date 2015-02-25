package org.springframework.cloud.configure.server;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.bind.PropertiesConfigurationFactory;
import org.springframework.cloud.configure.Environment;
import org.springframework.cloud.configure.PropertySource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.yaml.snakeyaml.Yaml;

@RestController
@RequestMapping("${spring.cloud.config.server.prefix:}")
public class EnvironmentController {
	
	@Qualifier("MultipleJGitEnvironmentRepository")
	private EnvironmentRepository repository;

	private EncryptionController encryption;

	private String defaultLabel = ConfigServerProperties.MASTER;

	private Map<String, String> overrides = new LinkedHashMap<String, String>();

	@Autowired
	public EnvironmentController(EnvironmentRepository repository,
			EncryptionController encryption) {
		super();
		this.repository = repository;
		this.encryption = encryption;
	}

	@RequestMapping("/{name}/{profiles:.*[^-].*}")
	public Environment master(@PathVariable String name, @PathVariable String profiles) {
		return labelled(name, profiles, defaultLabel);
	}

	@RequestMapping("/{name}/{profiles}/{label}")
	public Environment labelled(@PathVariable String name, @PathVariable String profiles,
			@PathVariable String label) {
		Environment environment = encryption.decrypt(repository.findOne(name, profiles,
				label));
		if (!overrides.isEmpty()) {
			environment.addFirst(new PropertySource("overrides", overrides));
		}
		return environment;
	}
	
	@RequestMapping("/{name}-{profiles}.properties")
	public ResponseEntity<String> properties(@PathVariable String name,
			@PathVariable String profiles) throws IOException {
		return labelledProperties(name, profiles, defaultLabel);
	}

	@RequestMapping("/{label}/{name}-{profiles}.properties")
	public ResponseEntity<String> labelledProperties(@PathVariable String name,
			@PathVariable String profiles, @PathVariable String label) throws IOException {
		if (name.contains("-") || profiles.contains("-")) {
			throw new IllegalArgumentException(
					"Properties output not supported for name or profiles containing hyphens");
		}
		Properties properties = convertToProperties(labelled(name, profiles, label));
		return getSuccess(sortLines(properties));
	}

	private String sortLines(Properties properties) throws IOException {
		List<String> list = new ArrayList<String>();
		for (Entry<Object, Object> entry : properties.entrySet()) {
			if (entry.getKey().equals("spring.profiles")) {
				continue;
			}
			String line = entry.getKey() + ": " + entry.getValue();
			list.add(line);
		}
		Collections.sort(list);
		StringBuilder output = new StringBuilder();
		for (String item : list) {
			if (output.length() > 0) {
				output.append("\n");
			}
			output.append(item);
		}
		return output.toString();
	}

	@RequestMapping({ "/{name}-{profiles}.yml", "/{name}-{profiles}.yaml" })
	public ResponseEntity<String> yaml(@PathVariable String name,
			@PathVariable String profiles) throws Exception {
		return labelledYaml(name, profiles, defaultLabel);
	}

	@RequestMapping({ "/{label}/{name}-{profiles}.yml", "/{label}/{name}-{profiles}.yaml" })
	public ResponseEntity<String> labelledYaml(@PathVariable String name,
			@PathVariable String profiles, @PathVariable String label) throws Exception {
		if (name.contains("-") || profiles.contains("-")) {
			throw new IllegalArgumentException(
					"YAML output not supported for name or profiles containing hyphens");
		}
		LinkedHashMap<String, Object> target = new LinkedHashMap<String, Object>();
		PropertiesConfigurationFactory<Map<String, Object>> factory = new PropertiesConfigurationFactory<Map<String, Object>>(
				target);
		Properties properties = convertToProperties(labelled(name, profiles, label));
		addArrays(target, properties);
		factory.setProperties(properties);
		factory.bindPropertiesToTarget();
		Map<String, Object> input = factory.getObject();
		return getSuccess(new Yaml().dumpAsMap(input));
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public void illegalArgument(HttpServletResponse response) throws IOException {
		response.sendError(HttpStatus.BAD_REQUEST.value());
	}

	private ResponseEntity<String> getSuccess(String body) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.TEXT_PLAIN);
		return new ResponseEntity<String>(body, headers, HttpStatus.OK);
	}

	/**
	 * Create Lists of the right size for any YAML arrays that are going to need to be
	 * bound. Some of this might be do-able in RelaxedDataBinder, but we need to do it
	 * here for now. Only supports arrays at leaf level currently (i.e. the properties
	 * keys end in [*]).
	 * 
	 * @param target the target Map
	 * @param properties the properties (with key names to check)
	 */
	private void addArrays(LinkedHashMap<String, Object> target, Properties properties) {
		for (String key : properties.stringPropertyNames()) {
			int index = key.indexOf("[");
			Map<String, Object> current = target;
			if (index > 0) {
				String stem = key.substring(0, index);
				String[] keys = StringUtils.split(stem, ".");
				for (int i = 0; i < keys.length - 1; i++) {
					if (current.get(keys[i]) == null) {
						LinkedHashMap<String, Object> map = new LinkedHashMap<String, Object>();
						current.put(keys[i], map);
						current = map;
					}
					else {
						@SuppressWarnings("unchecked")
						Map<String, Object> map = (Map<String, Object>) current
								.get(keys[i]);
						current = map;
					}
				}
				String name = keys[keys.length - 1];
				if (current.get(name) == null) {
					current.put(name, new ArrayList<Object>());
				}
				@SuppressWarnings("unchecked")
				List<Object> value = (List<Object>) current.get(name);
				int position = Integer
						.valueOf(key.substring(index + 1, key.indexOf("]")));
				while (position >= value.size()) {
					value.add("");
				}
			}
		}
	}

	private Properties convertToProperties(Environment profiles) {
		Properties map = new Properties();
		List<PropertySource> sources = new ArrayList<PropertySource>(profiles.getPropertySources());
		Collections.reverse(sources);
		for (PropertySource source : sources) {
			@SuppressWarnings("unchecked")
			Map<String, String> value = (Map<String, String>) source.getSource();
			map.putAll(value);
		}
		for (Entry<Object, Object> entry : map.entrySet()) {
			map.put(entry.getKey(), entry.getValue().toString());
		}
		return map;
	}

	/**
	 * @param defaultLabel
	 */
	public void setDefaultLabel(String defaultLabel) {
		this.defaultLabel = defaultLabel;
	}

	/**
	 * @param overrides the overrides to set
	 */
	public void setOverrides(Map<String, String> overrides) {
		this.overrides = overrides;
	}

}
