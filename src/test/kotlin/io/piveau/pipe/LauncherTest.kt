package io.piveau.pipe

import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(VertxExtension::class)
class LauncherTest {

    private val clusterConfig = JsonObject("""
            {
                "pipeRepositories": {
                    "system": {
                        "uri": "https://example.com/pipes.git",
                        "username": "username",
                        "token": "token",
                        "branch": "master"
                    }
                },
                "serviceDiscovery": {
                    "test-segment": {
                        "endpoints": {
                            "http": {
                                "address": "http://localhost:8098/pipe"
                            },
                            "eventbus": {
                                "address": "piveau.pipe.test1.queue"
                            }
                        }
                    }
                }
            }
        """.trimIndent())

    @Test
    fun clusterConfiguration(vertx: Vertx, testContext: VertxTestContext) {
        PiveauCluster.init(vertx, clusterConfig).setHandler {
            if (it.succeeded()) {
                val cluster = it.result()
                assert(cluster.availablePipeRepos().contains("system"))
                testContext.completeNow()
            } else {
                testContext.failNow(it.cause())
            }
        }
    }

    @Test
    fun launcherPipe(vertx: Vertx, testContext: VertxTestContext) {
        val checkpoint = testContext.checkpoint(2)

        vertx.createHttpServer().requestHandler {
            it.bodyHandler { body -> println(body) }
            it.response().setStatusCode(202).end()
            checkpoint.flag()
        }.listen(8098)

        PiveauCluster.init(vertx, clusterConfig).setHandler {
            if (it.succeeded()) {
                val cluster = it.result()
                val launcher = cluster.pipeLauncher()
                launcher.runPipe("test1", JsonObject()).setHandler {ar ->
                    if (ar.succeeded()) {
                        checkpoint.flag()
                    } else {
                        testContext.failNow(ar.cause())
                    }
                }
            } else {
                testContext.failNow(it.cause())
            }
        }
    }

    @Test
    fun launcherPipeWithData(vertx: Vertx, testContext: VertxTestContext) {
        val checkpoint = testContext.checkpoint(2)

        vertx.createHttpServer().requestHandler {
            it.bodyHandler { body -> println(body) }
            it.response().setStatusCode(202).end()
            checkpoint.flag()
        }.listen(8098)

        PiveauCluster.init(vertx, clusterConfig).setHandler {
            if (it.succeeded()) {
                val cluster = it.result()
                val launcher = cluster.pipeLauncher()
                launcher.runPipeWithData("test1", "This is test data", "text/plain").setHandler {ar ->
                    if (ar.succeeded()) {
                        checkpoint.flag()
                    } else {
                        testContext.failNow(ar.cause())
                    }
                }
            } else {
                testContext.failNow(it.cause())
            }
        }
    }

    @Test
    fun serviceDiscovery(vertx: Vertx, testContext: VertxTestContext) {
        PiveauCluster.init(Vertx.vertx(), clusterConfig).setHandler {
            if (it.succeeded()) {
                val endpoints = it.result().serviceDiscovery.resolve("test-segment")
                assert(endpoints.getJsonObject("http").getString("address") == "http://localhost:8098/pipe")

                val endpoint = it.result().serviceDiscovery.resolve("test-segment", "eventbus")
                assert(endpoint.getString("address") == "piveau.pipe.test1.queue")

                testContext.completeNow()
            } else {
                testContext.failNow(it.cause())
            }
        }
    }

}
