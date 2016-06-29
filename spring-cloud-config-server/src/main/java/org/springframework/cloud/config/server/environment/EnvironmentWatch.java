package org.springframework.cloud.config.server.environment;

/**
 * @author Spencer Gibb
 */
public interface EnvironmentWatch {
	String watch(String state);

	class Default implements EnvironmentWatch {

		@Override
		public String watch(String state) {
			return null;
		}
	}
}
