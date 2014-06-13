/*
 * Copyright 2013-2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.platform.context.environment;

import java.util.Map;

import org.springframework.boot.actuate.endpoint.Endpoint;
import org.springframework.boot.actuate.endpoint.EnvironmentEndpoint;
import org.springframework.boot.actuate.endpoint.mvc.MvcEndpoint;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * @author Dave Syer
 *
 */
public class EnvironmentManagerMvcEndpoint implements MvcEndpoint {

	private EnvironmentManager environment;
	private EnvironmentEndpoint delegate;

	public EnvironmentManagerMvcEndpoint(EnvironmentEndpoint delegate, EnvironmentManager enviroment) {
		this.delegate = delegate;
		environment = enviroment;
	}

	@RequestMapping(value = "", method = RequestMethod.POST)
	@ResponseBody
	public Object value(@RequestParam Map<String,String> params) {
		for (String name : params.keySet()) {
			environment.setProperty(name, params.get(name));
		}
		return params;
	}

	public void setEnvironmentManager(EnvironmentManager environment) {
		this.environment = environment;
	}

	@Override
	public String getPath() {
		return "/" + this.delegate.getId();
	}

	@Override
	public boolean isSensitive() {
		return this.delegate.isSensitive();
	}

	@Override
	@SuppressWarnings("rawtypes")
	public Class<? extends Endpoint> getEndpointType() {
		return this.delegate.getClass();
	}
}
