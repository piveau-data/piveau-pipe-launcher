package io.piveau.pipe

import com.google.common.collect.ImmutableList
import io.piveau.pipe.repositories.GitRepository
import io.vertx.core.*
import io.vertx.core.json.Json
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory

class PiveauCluster private constructor(private val vertx: Vertx, config: JsonObject = JsonObject()) {

    private val log = LoggerFactory.getLogger(this.javaClass)

    companion object {
        @JvmStatic
        fun init(vertx: Vertx, config: JsonObject = JsonObject()): Future<PiveauCluster> {
            val promise = Promise.promise<PiveauCluster>()
            vertx.executeBlocking<PiveauCluster>({
                it.complete(PiveauCluster(vertx, config))
            }) {
                if (it.succeeded()) {
                    promise.complete(it.result())
                } else {
                    promise.fail(it.cause())
                }
            }
            return promise.future()
        }
    }

    val serviceDiscovery = ServiceDiscovery(config.getJsonObject("serviceDiscovery", JsonObject()))

    internal val repos = mutableMapOf<String, GitRepository>()

    init {
        val pipeRepositories = config.getJsonObject("pipeRepositories", JsonObject())
        if (pipeRepositories.isEmpty) {
            log.warn("No pipe repositories configured!")
        } else {
            pipeRepositories.stream().filter { it.value is JsonObject }.forEach {
                with(it.value as JsonObject) {
                    repos[it.key] = GitRepository(
                        getString("uri"),
                        getString("branch"),
                        getString("username"),
                        getString("token")
                    )
                }
            }
        }
    }

    fun pipeLauncher(): PipeLauncher = PipeLauncher(vertx, this)

    fun availablePipeRepos(): ImmutableList<String> = ImmutableList.copyOf(repos.keys)

}
