/*
 * Copyright © 2012 Broadcom Inc. and/or its subsidiaries. All Rights Reserved.
 * Copyright 2012-present the original author or authors.
 */

package org.springframework.cloud.config.monitor;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.util.StreamUtils;
import org.springframework.web.filter.OncePerRequestFilter;

public class WebhookValidatorFilter extends OncePerRequestFilter {

	private List<WebhookRequestValidator> validators = Collections
		.singletonList(WebhookRequestValidator.INVALID_WEBHOOK_REQUEST);

	public WebhookValidatorFilter(List<WebhookRequestValidator> validators) {
		if (!validators.isEmpty()) {
			this.validators = validators;
		}
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		byte[] body = StreamUtils.copyToByteArray(request.getInputStream());
		HttpServletRequestWrapper replayable = new HttpServletRequestWrapper(request) {
			@Override
			public ServletInputStream getInputStream() {
				ByteArrayInputStream bais = new ByteArrayInputStream(body);
				return new ServletInputStream() {
					@Override
					public int read() throws IOException {
						return bais.read();
					}

					@Override
					public boolean isFinished() {
						return bais.available() == 0;
					}

					@Override
					public boolean isReady() {
						return true;
					}

					@Override
					public void setReadListener(ReadListener readListener) {
					}
				};
			}

			@Override
			public BufferedReader getReader() {
				return new BufferedReader(new InputStreamReader(new ByteArrayInputStream(body)));
			}
		};
		ServletServerHttpRequest servletRequest = new ServletServerHttpRequest(replayable);
		List<WebhookRequestValidator> matching = validators.stream()
			.filter(validator -> validator.shouldValidate(servletRequest))
			.toList();
		if (matching.isEmpty() || !matching.stream().allMatch(validator -> validator.validate(servletRequest))) {
			response.sendError(HttpServletResponse.SC_FORBIDDEN);
			return;
		}
		filterChain.doFilter(replayable, response);
	}

}
