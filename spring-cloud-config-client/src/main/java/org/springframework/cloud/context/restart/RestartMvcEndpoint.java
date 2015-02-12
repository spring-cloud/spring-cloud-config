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
package org.springframework.cloud.context.restart;

import java.util.Collections;
import java.util.Map;

import org.springframework.boot.actuate.endpoint.mvc.EndpointMvcAdapter;
import org.springframework.boot.actuate.endpoint.mvc.MvcEndpoint;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.cloud.endpoint.GenericPostableMvcEndpoint;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * MVC endpoint to allow an application to be restarted on a POST (to /restart by
 * default).
 * 
 * @author Dave Syer
 *
 */
public class RestartMvcEndpoint extends EndpointMvcAdapter {

	public RestartMvcEndpoint(RestartEndpoint delegate) {
		super(delegate);
	}

	@RequestMapping(method = RequestMethod.POST)
	@ResponseBody
	@Override
	public Object invoke() {
		if (!getDelegate().isEnabled()) {
			return new ResponseEntity<Map<String, String>>(Collections.singletonMap(
					"message", "This endpoint is disabled"), HttpStatus.NOT_FOUND);
		}
		Thread thread = new Thread(new Runnable() {
			@Override
			public void run() {
				RestartMvcEndpoint.super.invoke();
			}
		});
		thread.setDaemon(false);
		thread.start();
		return Collections.singletonMap("message", "Restarting");
	}

	public MvcEndpoint getPauseEndpoint() {
		return new GenericPostableMvcEndpoint(
				((RestartEndpoint) getDelegate()).getPauseEndpoint());
	}

	public MvcEndpoint getResumeEndpoint() {
		return new GenericPostableMvcEndpoint(
				((RestartEndpoint) getDelegate()).getResumeEndpoint());
	}

}
