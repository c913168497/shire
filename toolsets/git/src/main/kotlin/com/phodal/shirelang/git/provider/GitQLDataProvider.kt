package com.phodal.shirelang.git.provider

import com.intellij.ide.DataManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.phodal.shirecore.ShireCoreBundle
import com.phodal.shirecore.provider.shire.ShireQLDataProvider
import com.phodal.shirecore.provider.shire.ShireQLDataType
import com.phodal.shirecore.vcs.GitEntity
import com.phodal.shirecore.vcs.ShireVcsCommit
import git4idea.GitCommit
import git4idea.history.GitHistoryUtils
import git4idea.repo.GitRepositoryManager
import kotlinx.coroutines.future.await
import java.util.concurrent.CompletableFuture

class GitQLDataProvider : ShireQLDataProvider {
    override fun lookupGitData(myProject: Project, dataTypes: List<ShireQLDataType>): Map<ShireQLDataType, List<GitEntity>?> {
        val result = mutableMapOf<ShireQLDataType, List<GitEntity>?>()
        dataTypes.forEach {
            when (it) {
                ShireQLDataType.GIT_COMMIT -> {
                    val commits = buildCommits(myProject)
                    result[it] = commits
                }
                ShireQLDataType.GIT_BRANCH -> TODO()
                ShireQLDataType.GIT_FILE_COMMIT -> TODO()
                ShireQLDataType.GIT_FILE_BRANCH -> TODO()
                else -> {
                    result[it] = null
                }
            }
        }

        return result
    }

    private fun buildCommits(myProject: Project): List<GitEntity> {
        val repository = GitRepositoryManager.getInstance(myProject).repositories.firstOrNull() ?: return emptyList()
        val branchName = repository.currentBranchName

        /**
         * Refs to [com.intellij.execution.process.OSProcessHandler.checkEdtAndReadAction], we should handle in this
         * way, another example can see in [git4idea.GitPushUtil.findOrPushRemoteBranch]
         */
        val future = CompletableFuture<List<GitCommit>>()
        val task = object : Task.Backgroundable(myProject, ShireCoreBundle.message("shire.ref.loading"), false) {
            override fun run(indicator: ProgressIndicator) {
                val commits: List<GitCommit> = try {
                    // in some case, maybe not repo or branch, so we should handle it
                    GitHistoryUtils.history(project, repository.root, branchName)
                } catch (e: Exception) {
                    emptyList()
                }

                future.complete(commits)
            }
        }

        ProgressManager.getInstance()
            .runProcessWithProgressAsynchronously(task, BackgroundableProcessIndicator(task))

        val results: MutableList<ShireVcsCommit> = mutableListOf()
        runBlockingCancellable {
            future.await().forEach {
                val commit = ShireVcsCommit(
                    it.id.asString(),
                    it.author.name,
                    it.author.email,
                    it.authorTime,
                    it.committer.name,
                    it.committer.email,
                    it.commitTime,
                    it.fullMessage,
                    it.fullMessage,
                )

                results.add(commit)
            }
        }

        return results.toList()
    }
}
