package org.springframework.cloud.config.server;

import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;

import javax.servlet.http.HttpServletResponse;

import org.springframework.boot.bind.PropertiesConfigurationFactory;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
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

	private static final String MAP_PREFIX = "map";

	private EnvironmentRepository repository;

	private EncryptionController encryption;

	private String defaultLabel = ConfigServerProperties.MASTER;

	private Map<String, String> overrides = new LinkedHashMap<String, String>();

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

	@RequestMapping("/{name}/{profiles}/{label:.*}")
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
		validateNameAndProfiles(name, profiles);
		Map<String, Object> properties = convertToProperties(labelled(name, profiles,
				label));
		return getSuccess(getPropertiesString(properties));
	}

	@RequestMapping("{name}-{profiles}.json")
	public ResponseEntity<Map<String, Object>> jsonProperties(@PathVariable String name,
			@PathVariable String profiles) throws IOException {
		return labelledJsonProperties(name, profiles, defaultLabel);
	}

	@RequestMapping("/{label}/{name}-{profiles}.json")
	public ResponseEntity<Map<String, Object>> labelledJsonProperties(@PathVariable String name,
			@PathVariable String profiles, @PathVariable String label) throws IOException {
		validateNameAndProfiles(name, profiles);
		Map<String, Object> properties = convertToProperties(labelled(name, profiles,
				label));
		return getSuccess(properties);
	}

	private String getPropertiesString(Map<String, Object> properties) {
		StringBuilder output = new StringBuilder();
		for (Entry<String, Object> entry : properties.entrySet()) {
			if (output.length() > 0) {
				output.append("\n");
			}
			String line = entry.getKey() + ": " + entry.getValue();
			output.append(line);
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
		Map<String, Object> data = convertToProperties(labelled(name, profiles,
				label));
		LinkedHashMap<String, Object> properties = new LinkedHashMap<String, Object>();
		for (String key : data.keySet()) {
			properties.put(MAP_PREFIX + "." + key, data.get(key));
		}
		addArrays(target, properties);
		MutablePropertySources propertySources = new MutablePropertySources();
		propertySources.addFirst(new MapPropertySource("properties", properties));
		factory.setPropertySources(propertySources);
		factory.bindPropertiesToTarget();
		return getSuccess(new Yaml().dumpAsMap(target.get(MAP_PREFIX)));
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public void illegalArgument(HttpServletResponse response) throws IOException {
		response.sendError(HttpStatus.BAD_REQUEST.value());
	}

	private void validateNameAndProfiles(String name, String profiles) {
		if (name.contains("-") || profiles.contains("-")) {
			throw new IllegalArgumentException(
					"Properties output not supported for name or profiles containing hyphens");
		}
	}

	private HttpHeaders getHttpHeaders(MediaType mediaType) {
		HttpHeaders httpHeaders = new HttpHeaders();
		httpHeaders.setContentType(mediaType);
		return httpHeaders;
	}

	private ResponseEntity<String> getSuccess(String body) {
		return new ResponseEntity<>(body, getHttpHeaders(MediaType.TEXT_PLAIN), HttpStatus.OK);
	}

	private ResponseEntity<Map<String, Object>> getSuccess(Map<String, Object> body) {
		return new ResponseEntity<>(body, getHttpHeaders(MediaType.APPLICATION_JSON), HttpStatus.OK);
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
	private void addArrays(LinkedHashMap<String, Object> target,
			Map<String, Object> properties) {
		for (String key : properties.keySet()) {
			int index = key.indexOf("[");
			Map<String, Object> current = target;
			if (index > 0) {
				String stem = key.substring(0, index);
				String[] keys = StringUtils.delimitedListToStringArray(stem, ".");
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
					if (key.indexOf("].", index) > 0) {
						value.add(new LinkedHashMap<String, Object>());
					}
					else {
						value.add("");
					}
				}
			}
		}
	}

	private Map<String, Object> convertToProperties(Environment profiles) {
		Map<String, Object> map = new TreeMap<String, Object>();
		List<PropertySource> sources = new ArrayList<PropertySource>(
				profiles.getPropertySources());
		Collections.reverse(sources);
		for (PropertySource source : sources) {
			@SuppressWarnings("unchecked")
			Map<String, String> value = (Map<String, String>) source.getSource();
			map.putAll(value);
		}
        postProcessProperties(map);
		return map;
	}

	private void postProcessProperties(Map<String, Object> propertiesMap) {
		for(String key : propertiesMap.keySet()) {
			if(key.equals("spring.profiles")) {
				propertiesMap.remove(key);
			}
		}
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
