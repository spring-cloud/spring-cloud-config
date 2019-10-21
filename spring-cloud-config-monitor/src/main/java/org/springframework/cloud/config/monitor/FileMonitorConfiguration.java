/*
 * Copyright 2015-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.config.monitor;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.config.server.environment.AbstractScmEnvironmentRepository;
import org.springframework.cloud.config.server.environment.NativeEnvironmentRepository;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.FileUrlResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpHeaders;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.util.PatternMatchUtils;

/**
 * Configuration for a file watcher that detects changes in local files related to the
 * environment repository. If any files change the {@link PropertyPathEndpoint} is pinged
 * with the paths of the files. This applies to the source files of a local git repository
 * (i.e. a git repository with a "file:" URI) or to a native repository.
 *
 * @author Dave Syer
 * @author Gilles Robert
 *
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class FileMonitorConfiguration implements SmartLifecycle, ResourceLoaderAware {

	private static final Log log = LogFactory.getLog(FileMonitorConfiguration.class);

	@Autowired
	private PropertyPathEndpoint endpoint;

	@Autowired(required = false)
	private List<AbstractScmEnvironmentRepository> scmRepositories;

	@Autowired(required = false)
	private NativeEnvironmentRepository nativeEnvironmentRepository;

	private boolean running;

	private WatchService watcher;

	private Set<Path> directory;

	private int phase;

	private boolean autoStartup = true;

	private ResourceLoader resourceLoader;

	private String[] excludes = new String[] { ".*", "#*", "*#" };

	@Override
	public void setResourceLoader(ResourceLoader resourceLoader) {
		this.resourceLoader = resourceLoader;
	}

	@Override
	public int getPhase() {
		return this.phase;
	}

	/**
	 * see {@link #getPhase()}.
	 * @param phase the phase.
	 */
	public void setPhase(int phase) {
		this.phase = phase;
	}

	@Override
	public boolean isRunning() {
		return this.running;
	}

	/**
	 * @param running true if running.
	 * @see #isRunning()
	 */
	public void setRunning(boolean running) {
		this.running = running;
	}

	@Override
	public boolean isAutoStartup() {
		return this.autoStartup;
	}

	/**
	 * @param autoStartup true to auto start.
	 * @see #isAutoStartup()
	 */
	public void setAutoStartup(boolean autoStartup) {
		this.autoStartup = autoStartup;
	}

	@Override
	public synchronized void start() {
		if (!this.running) {
			this.directory = getFileRepo();
			if (this.directory != null && !this.directory.isEmpty()) {
				log.info("Monitoring for local config changes: " + this.directory);
				try {
					this.watcher = FileSystems.getDefault().newWatchService();
					for (Path path : this.directory) {
						walkDirectory(path);
					}
				}
				catch (IOException e) {
				}
			}
			else {
				log.info("Not monitoring for local config changes");
			}
			this.running = true;
		}
	}

	@Override
	public synchronized void stop() {
		if (this.running) {
			if (this.watcher != null) {
				try {
					this.watcher.close();
				}
				catch (IOException e) {
					log.error("Failed to close watcher for " + this.directory.toString(),
							e);
				}
			}
			this.running = false;
		}
	}

	@Override
	public void stop(Runnable callback) {
		stop();
		callback.run();
	}

	@Scheduled(fixedRateString = "${spring.cloud.config.server.monitor.fixedDelay:5000}")
	public void poll() {
		for (File file : filesFromEvents()) {
			this.endpoint.notifyByPath(new HttpHeaders(), Collections
					.<String, Object>singletonMap("path", file.getAbsolutePath()));
		}
	}

	private Set<Path> getFileRepo() {
		if (this.scmRepositories != null) {
			String repositoryUri = null;
			Set<Path> paths = new LinkedHashSet<>();
			try {
				for (AbstractScmEnvironmentRepository repository : scmRepositories) {
					repositoryUri = repository.getUri();
					Resource resource = this.resourceLoader.getResource(repositoryUri);
					if (resource instanceof FileSystemResource
							|| resource instanceof FileUrlResource) {
						paths.add(Paths.get(resource.getURI()));
					}
				}
				return paths;
			}
			catch (IOException e) {
				log.error("Cannot resolve URI for path: " + repositoryUri);
			}
		}
		if (this.nativeEnvironmentRepository != null) {
			Set<Path> paths = new LinkedHashSet<>();
			for (String path : this.nativeEnvironmentRepository.getSearchLocations()) {
				Resource resource = this.resourceLoader.getResource(path);
				if (resource.exists()) {
					try {
						paths.add(Paths.get(resource.getURI()));
					}
					catch (Exception e) {
						log.error("Cannot resolve URI for path: " + path);
					}
				}
			}
			return paths;
		}
		return null;
	}

	private Set<File> filesFromEvents() {
		Set<File> files = new LinkedHashSet<File>();
		if (this.watcher == null) {
			return files;
		}
		WatchKey key = this.watcher.poll();
		while (key != null) {
			for (WatchEvent<?> event : key.pollEvents()) {
				if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE
						|| event.kind() == StandardWatchEventKinds.ENTRY_MODIFY) {
					Path item = (Path) event.context();
					File file = new File(((Path) key.watchable()).toAbsolutePath()
							+ File.separator + item.getFileName());
					if (file.isDirectory()) {
						files.addAll(walkDirectory(file.toPath()));
					}
					else {
						if (!file.getPath().contains(".git") && !PatternMatchUtils
								.simpleMatch(this.excludes, file.getName())) {
							if (log.isDebugEnabled()) {
								log.debug("Watch Event: " + event.kind() + ": " + file);
							}
							files.add(file);
						}
					}
				}
				else if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
					if (log.isDebugEnabled()) {
						log.debug("Watch Event: " + event.kind() + ": context: "
								+ event.context());
					}
					if (event.context() != null && event.context() instanceof Path) {
						files.addAll(walkDirectory((Path) event.context()));
					}
					else {
						for (Path path : this.directory) {
							files.addAll(walkDirectory(path));
						}
					}
				}
				else {
					if (log.isDebugEnabled()) {
						log.debug("Watch Event: " + event.kind() + ": context: "
								+ event.context());
					}
				}
			}
			key.reset();
			key = this.watcher.poll();
		}
		return files;
	}

	private Set<File> walkDirectory(Path directory) {
		final Set<File> walkedFiles = new LinkedHashSet<File>();
		try {
			registerWatch(directory);
			Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {

				@Override
				public FileVisitResult preVisitDirectory(Path dir,
						BasicFileAttributes attrs) throws IOException {
					FileVisitResult fileVisitResult = super.preVisitDirectory(dir, attrs);
					// No need to monitor the git metadata
					if (dir.toFile().getPath().contains(".git")) {
						return FileVisitResult.SKIP_SUBTREE;
					}
					registerWatch(dir);
					return fileVisitResult;
				}

				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
						throws IOException {
					FileVisitResult fileVisitResult = super.visitFile(file, attrs);
					walkedFiles.add(file.toFile());
					return fileVisitResult;
				}

			});
		}
		catch (IOException e) {
			log.error("Failed to walk directory: " + directory.toString(), e);
		}
		return walkedFiles;
	}

	private void registerWatch(Path dir) throws IOException {
		if (log.isDebugEnabled()) {
			log.debug("registering: " + dir + " for file creation events");
		}
		try {
			dir.register(this.watcher, StandardWatchEventKinds.ENTRY_CREATE,
					StandardWatchEventKinds.ENTRY_MODIFY);
		}
		catch (IOException e) {
			throw e;
		}
		catch (Exception e) {
			throw new IOException("Cannot register watcher for " + dir, e);
		}
	}

}
