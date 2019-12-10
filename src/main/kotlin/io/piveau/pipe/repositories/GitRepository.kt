package io.piveau.pipe.repositories

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.MergeResult
import org.eclipse.jgit.api.errors.GitAPIException
import org.eclipse.jgit.internal.storage.file.FileRepository
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.net.URL
import java.net.URLEncoder
import java.nio.file.Path
import java.nio.file.Paths

class GitRepository(
    private val uri: String,
    private val branch: String = "master",
    private val username: String? = null,
    private val token: String? = null
) {

    private val log = LoggerFactory.getLogger(this.javaClass)

    private val localPath = Paths.get("repositories").resolve(uri.replace("[^\\w\\s]".toRegex(), "")).resolve(branch)

    init {
        if (localPath.toFile().exists()) {
            pullRepo()
        } else {
            cloneRepo()
        }
    }

    fun jsonFiles(): Array<File> = localPath.toFile().listFiles { _, name -> name.endsWith(".json") } ?: arrayOf()

    fun resolve(path: String): Path = localPath.resolve(path)

    private fun cloneRepo() {
        val finalUri = token?.let { uri } ?: URL(uri).let {
            "${it.protocol}://$username:${URLEncoder.encode(
                token,
                "UTF-8"
            )}@${it.authority}${it.file}"
        }

        val cloneCommand = Git.cloneRepository()
            .setURI(finalUri)
            .setBranch(branch)
            .setDirectory(localPath.toFile())
            .setCredentialsProvider(UsernamePasswordCredentialsProvider(username, token))

        try {
            cloneCommand.call().use { log.debug("Git clone successful") }
        } catch (e: Exception) {
            log.error("Calling clone command", e)
        }

    }

    fun pullRepo(): MergeResult.MergeStatus {
        try {
            FileRepository(localPath.resolve(".git").toFile()).use { localRepo ->
                Git(localRepo).use { repo ->
                    val pullCommand = repo.pull()
                    pullCommand.setCredentialsProvider(UsernamePasswordCredentialsProvider(username, token))

                    val pResult = pullCommand.call()
                    val mResult = pResult.mergeResult

                    if (!mResult.mergeStatus.isSuccessful) {
                        log.warn("Could not merge repository: {}", mResult)
                    }
                    return mResult.mergeStatus
                }
            }
        } catch (e: IOException) {
            log.error("Calling pull command", e)
        } catch (e: GitAPIException) {
            log.error("Calling pull command", e)
        }
        return MergeResult.MergeStatus.FAILED
    }

}
