/*
 * Copyright 2013-present the original author or authors.
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

package org.springframework.cloud.config.server.support;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;

/**
 * Static file-system utilities shared by SCM-backed repository implementations to prevent
 * following symlinks during local clone, checkout, and cleanup operations.
 *
 * <p>
 * None of these methods follow directory symbolic links; a symlink encountered at the
 * target path or inside the directory tree is treated as a plain file-system entry and
 * never traversed into its target.
 *
 * @author Ryan Baxter
 */
public final class ScmFileUtils {

	private ScmFileUtils() {
	}

	/**
	 * Recreates {@code dir} as an empty, exclusively-created real directory suitable for
	 * an SCM clone or checkout. Any existing content or symlink at the leaf path is
	 * removed without following symlinks. The leaf is then created with
	 * {@link Files#createDirectory}, which is an exclusive OS-level create: a concurrent
	 * replacement of the path with a symlink surfaces as
	 * {@link FileAlreadyExistsException} rather than silently redirecting the SCM
	 * operation. Returns the canonicalized real path of the freshly created directory.
	 * @param dir the directory to recreate
	 * @return the real path of the newly created directory
	 * @throws IOException if the directory cannot be created or a concurrent path
	 * substitution is detected
	 */
	public static Path recreateSecureDirectory(File dir) throws IOException {
		Path path = dir.toPath().toAbsolutePath().normalize();
		Path parent = path.getParent();
		if (parent == null) {
			throw new IllegalStateException("Directory cannot be a filesystem root: " + dir);
		}
		Files.createDirectories(parent);
		ensureLeafAbsent(path);
		try {
			Files.createDirectory(path);
		}
		catch (FileAlreadyExistsException ex) {
			throw new IllegalStateException(
					"Could not create exclusive directory (concurrent path substitution?): " + dir, ex);
		}
		return path.toRealPath(LinkOption.NOFOLLOW_LINKS);
	}

	/**
	 * Verifies that {@code dir} still resolves (without following symlinks) to
	 * {@code expectedReal} — the path returned by {@link #recreateSecureDirectory(File)}
	 * — immediately before invoking the SCM client. A mismatch indicates a concurrent
	 * path substitution.
	 * @param dir the directory to re-verify
	 * @param expectedReal the real path returned by {@link #recreateSecureDirectory}
	 */
	public static void assertDirectoryStillResolvesTo(File dir, Path expectedReal) {
		Path current;
		try {
			current = dir.toPath().toAbsolutePath().normalize().toRealPath(LinkOption.NOFOLLOW_LINKS);
		}
		catch (IOException ex) {
			throw new IllegalStateException("Could not re-resolve directory before SCM operation: " + dir, ex);
		}
		if (!current.equals(expectedReal)) {
			throw new IllegalStateException(
					"Directory changed before SCM operation (possible path substitution): " + dir);
		}
	}

	/**
	 * Deletes all entries under {@code dir} without following directory symlinks, leaving
	 * {@code dir} itself in place. A directory-symlink entry inside the tree is deleted
	 * as a link rather than recursed into, so deletion cannot escape outside the tree.
	 * @param dir the root of the directory tree to empty
	 * @throws IOException if deletion fails
	 */
	public static void deleteDirectoryContents(Path dir) throws IOException {
		Path base = dir.toAbsolutePath().normalize();
		// EnumSet with no FOLLOW_LINKS so directory symlinks are not traversed.
		Files.walkFileTree(base, EnumSet.noneOf(FileVisitOption.class), Integer.MAX_VALUE,
				new SimpleFileVisitor<Path>() {

					@Override
					public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
						assertPathUnderBase(file, base);
						Files.delete(file);
						return FileVisitResult.CONTINUE;
					}

					@Override
					public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
						throw exc;
					}

					@Override
					public FileVisitResult postVisitDirectory(Path directory, IOException exc) throws IOException {
						if (exc != null) {
							throw exc;
						}
						assertPathUnderBase(directory, base);
						if (!directory.equals(base)) {
							Files.delete(directory);
						}
						return FileVisitResult.CONTINUE;
					}

				});
	}

	/**
	 * Asserts that no component of {@code path} — from the root to the leaf — is a
	 * symbolic link. Unlike {@link Path#toRealPath(LinkOption...)} with
	 * {@link LinkOption#NOFOLLOW_LINKS}, which on Linux/macOS silently resolves
	 * intermediate parent symlinks rather than failing, this method checks every
	 * component of the path individually via {@link Files#isSymbolicLink}.
	 * @param path an absolute, normalized path to validate
	 * @throws IllegalStateException if any component of the path is a symbolic link
	 */
	public static void assertNoSymlinkInPath(Path path) {
		Path current = path.getRoot();
		if (current == null) {
			return;
		}
		for (Path component : path) {
			current = current.resolve(component);
			if (Files.isSymbolicLink(current)) {
				throw new IllegalStateException("Path component must not be a symbolic link: " + current);
			}
		}
	}

	/**
	 * Asserts that {@code candidate} is under {@code base}; throws
	 * {@link IllegalStateException} if not. Provides defense-in-depth in case the
	 * file-tree walk ever yields a path outside the intended root.
	 * @param candidate the path to check
	 * @param base the expected root
	 */
	static void assertPathUnderBase(Path candidate, Path base) {
		Path normalized = candidate.toAbsolutePath().normalize();
		Path normalizedBase = base.toAbsolutePath().normalize();
		if (!normalized.startsWith(normalizedBase)) {
			throw new IllegalStateException("Path is outside base directory: " + candidate);
		}
	}

	/**
	 * Removes the leaf at {@code path} — whether a symlink, regular file, or directory —
	 * without following symlinks into any target. After this method returns the path does
	 * not exist on the file system.
	 */
	private static void ensureLeafAbsent(Path path) throws IOException {
		if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
			return;
		}
		if (Files.isSymbolicLink(path)) {
			Files.delete(path);
			return;
		}
		deleteDirectoryContents(path);
		if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
			Files.delete(path);
		}
	}

}
