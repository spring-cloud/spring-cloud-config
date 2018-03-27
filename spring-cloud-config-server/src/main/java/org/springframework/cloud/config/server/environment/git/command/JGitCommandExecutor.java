/*
 * Copyright 2013-2018 the original author or authors.
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
package org.springframework.cloud.config.server.environment.git.command;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.eclipse.jgit.api.CheckoutCommand;
import org.eclipse.jgit.api.CreateBranchCommand;
import org.eclipse.jgit.api.DeleteBranchCommand;
import org.eclipse.jgit.api.FetchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.api.MergeCommand;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.BranchTrackingStatus;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.transport.FetchResult;
import org.eclipse.jgit.transport.TagOpt;

import static java.lang.String.format;

/**
 * @author Taras Danylchuk
 */
public class JGitCommandExecutor {

	protected Log logger = LogFactory.getLog(getClass());

	private final JGitCommandConfigurer jGitCommandConfigurer;

	public JGitCommandExecutor(JGitCommandConfigurer jGitCommandConfigurer) {
		this.jGitCommandConfigurer = jGitCommandConfigurer;
	}

	public MergeResult safeMerge(Git git, String label) {
		try {
			MergeCommand merge = git.merge();
			merge.include(git.getRepository().findRef("origin/" + label));
			MergeResult result = merge.call();
			if (!result.getMergeStatus().isSuccessful()) {
				this.logger.warn("Merged from remote " + label + " with result "
						+ result.getMergeStatus());
			}
			return result;
		} catch (Exception ex) {
			String message = "Could not merge remote for " + label + " remote: " + git
					.getRepository().getConfig().getString("remote", "origin", "url");
			warn(message, ex);
			return null;
		}
	}

	public Ref safeHardReset(Git git, String label, String ref) {
		ResetCommand reset = git.reset();
		reset.setRef(ref);
		reset.setMode(ResetCommand.ResetType.HARD);
		try {
			Ref resetRef = reset.call();
			if (resetRef != null) {
				this.logger.info(
						"Reset label " + label + " to version " + resetRef.getObjectId());
			}
			return resetRef;
		} catch (Exception ex) {
			String message = "Could not reset to remote for " + label + " (current ref="
					+ ref + "), remote: " + git.getRepository().getConfig()
					.getString("remote", "origin", "url");
			warn(message, ex);
			return null;
		}
	}

	public Ref checkout(Git git, String label) throws GitAPIException {
		CheckoutCommand checkout = git.checkout();
		if (shouldTrack(git, label)) {
			trackBranch(git, checkout, label);
		}
		checkout.setName(label);
		return checkout.call();
	}

	public FetchResult safeFetch(Git git, String label, boolean removeDeletedRefs) {
		FetchCommand fetch = git.fetch();
		fetch.setRemote("origin");
		fetch.setTagOpt(TagOpt.FETCH_TAGS);
		fetch.setRemoveDeletedRefs(removeDeletedRefs);

		jGitCommandConfigurer.configureCommand(fetch);
		try {
			FetchResult result = fetch.call();
			if (result.getTrackingRefUpdates() != null
					&& result.getTrackingRefUpdates().size() > 0) {
				logger.info("Fetched for remote " + label + " and found "
						+ result.getTrackingRefUpdates().size() + " updates");
			}
			return result;
		} catch (Exception ex) {
			String message = "Could not fetch remote for " + label + " remote: " + git
					.getRepository().getConfig().getString("remote", "origin", "url");
			warn(message, ex);
			return null;
		}
	}

	public List<String> safeDeleteBranches(Git git, Collection<String> branchesToDelete, String defaultLabel) {
		try {
			//make sure that deleted branch not a current one
			checkout(git, defaultLabel);
			return deleteBranches(git, branchesToDelete);
		} catch (Exception ex) {
			String message = format("Failed to delete %s branches.", branchesToDelete);
			warn(message, ex);
			return Collections.emptyList();
		}
	}

	private List<String> deleteBranches(Git git, Collection<String> branchesToDelete) throws GitAPIException {
		DeleteBranchCommand deleteBranchCommand = git.branchDelete();
		deleteBranchCommand.setBranchNames(branchesToDelete.toArray(new String[0]));
				//local branch can contain data which is not merged to HEAD - force delete it anyway, since local copy should be R/O
		deleteBranchCommand.setForce(true);
		List<String> resultList = deleteBranchCommand.call();
		logger.info(format("Deleted %s branches from %s branches to delete.", resultList, branchesToDelete));
		return resultList;
	}

	public boolean isClean(Git git, String label) {
		try {
			BranchTrackingStatus trackingStatus = BranchTrackingStatus.of(git.getRepository(), label);
			boolean isBranchAhead = trackingStatus != null && trackingStatus.getAheadCount() > 0;
			return status(git).isClean() && !isBranchAhead;
		} catch (Exception e) {
			String message = "Could not execute status command on local repository. Cause: ("
					+ e.getClass().getSimpleName() + ") " + e.getMessage();
			warn(message, e);
			return false;
		}
	}

	public Status status(Git git) throws GitAPIException {
		return git.status().call();
	}

	private boolean shouldTrack(Git git, String label) throws GitAPIException {
		return isBranch(git, label) && !isLocalBranch(git, label);
	}

	public boolean isBranch(Git git, String label) throws GitAPIException {
		return containsBranch(git, label, ListBranchCommand.ListMode.ALL);
	}

	private boolean isLocalBranch(Git git, String label) throws GitAPIException {
		return containsBranch(git, label, null);
	}

	private boolean containsBranch(Git git, String label, ListBranchCommand.ListMode listMode)
			throws GitAPIException {
		ListBranchCommand command = git.branchList();
		if (listMode != null) {
			command.setListMode(listMode);
		}
		List<Ref> branches = command.call();
		for (Ref ref : branches) {
			if (ref.getName().endsWith("/" + label)) {
				return true;
			}
		}
		return false;
	}

	private void trackBranch(Git git, CheckoutCommand checkout, String label) {
		checkout.setCreateBranch(true);
		checkout.setUpstreamMode(CreateBranchCommand.SetupUpstreamMode.TRACK);
		checkout.setStartPoint("origin/" + label);
	}

	protected void warn(String message, Throwable ex) {
		logger.warn(message);
		if (logger.isDebugEnabled()) {
			logger.debug("Stacktrace for: " + message, ex);
		}
	}

}
