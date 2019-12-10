package io.piveau.pipe

import com.fasterxml.jackson.databind.node.ObjectNode
import com.google.common.collect.ImmutableList
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.http.HttpMethod
import io.vertx.core.json.Json
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.client.WebClient
import org.eclipse.jgit.api.MergeResult
import org.everit.json.schema.ValidationException
import org.slf4j.LoggerFactory
import java.net.URL
import java.util.*

class PipeLauncher(vertx: Vertx, private val cluster: PiveauCluster) {

    private val log = LoggerFactory.getLogger(this.javaClass)

    private val client = WebClient.create(vertx)

    private val pipes = mutableMapOf<String, Pipe>()

    init {
        updatePipes(vertx)
        vertx.setPeriodic(vertx.orCreateContext.config().getLong("PIVEAU_LAUNCHER_PERIODIC_UPDATE", 60000)) {
            log.trace("Launcher periodic update trigger.")
            if (cluster.repos.values.any { it.pullRepo() != MergeResult.MergeStatus.ALREADY_UP_TO_DATE }) {
                log.debug("At least one repo updated, reread all pipes.")
                updatePipes(vertx)
            }
        }
    }

    private fun updatePipes(vertx: Vertx) {
        pipes.clear()
        if (vertx.fileSystem().existsBlocking("pipes")) {
            readPipes(vertx, vertx.fileSystem().readDirBlocking("pipes", "^([a-zA-Z0-9\\s_\\\\.\\-\\(\\):])+\\.(json)\$"))
        }
        cluster.repos.values.forEach {
            readPipes(vertx, it.jsonFiles().map { f -> f.absolutePath })
        }
    }

    fun runPipeWithBinaryData(
        pipeName: String,
        data: ByteArray,
        mimeType: String? = null,
        info: ObjectNode? = null,
        configs: JsonObject = JsonObject()
    ): Future<Void> = pipes[pipeName]?.let {
        it.body.segments.minBy { segment -> segment.header.segmentNumber }?.apply {
            body.payload = Payload(
                header = PayloadHeader(
                    dataType = DataType.base64,
                    seqNumber = 0
                ),
                body = PayloadBody(
                    data = Base64.getEncoder().encodeToString(data),
                    dataMimeType = mimeType,
                    dataInfo = info
                )
            )
        }
        runPipe(it, configs)
    } ?: Future.failedFuture("No such pipe!")

    fun runPipeWithData(
        pipeName: String,
        data: String,
        mimeType: String? = null,
        info: ObjectNode? = null,
        configs: JsonObject = JsonObject()
    ): Future<Void> = pipes[pipeName]?.let {
        it.body.segments.minBy { segment -> segment.header.segmentNumber }?.apply {
            body.payload = Payload(
                header = PayloadHeader(
                    dataType = DataType.text,
                    seqNumber = 0
                ),
                body = PayloadBody(
                    data = data,
                    dataMimeType = mimeType,
                    dataInfo = info
                )
            )
        }
        runPipe(it, configs)
    } ?: Future.failedFuture("No such pipe!")

    fun runPipe(pipeName: String, configs: JsonObject = JsonObject()): Future<Void> = pipes[pipeName]?.let {
        runPipe(it, configs)
    } ?: Future.failedFuture("No such pipe!")

    fun runPipe(pipe: Pipe, configs: JsonObject = JsonObject()): Future<Void> {

        pipe.body.segments.forEach { (header, body) ->
            if (body.endpoint == null) {
                val endpoint = cluster.serviceDiscovery.resolve(header.name, "http")
                body.endpoint = Json.decodeValue(endpoint.toString(), Endpoint::class.java)
            }
        }

        return sendPipe(pipe)
    }

    fun isPipeAvailable(name: String) = pipes.contains(name)

    fun availablePipes(): ImmutableList<Pipe> = ImmutableList.copyOf(pipes.values)

    fun getPipe(name: String) = pipes[name];

    private fun sendPipe(pipe: Pipe): Future<Void> {
        val pipeManager = PipeManager.create(pipe)
        val endpoint = pipeManager.currentEndpoint ?: return Future.failedFuture("No endpoint available")
        val buffer = Json.encodeToBuffer(pipeManager.pipe)
        val address = URL(endpoint.address!!)

        val promise = Promise.promise<Void>()
        client.request(HttpMethod.valueOf(endpoint.method ?: "POST"), address.port, address.host, address.path)
            .putHeader("Content-Type", "application/json").sendBuffer(buffer) { ar ->
                if (ar.succeeded()) {
                    when (ar.result().statusCode()) {
                        200, 202 -> promise.complete()
                        else -> promise.fail("${ar.result().statusCode()} - ${ar.result().statusMessage()}")
                    }
                } else {
                    promise.fail(ar.cause())
                }
            }

        return promise.future()
    }

    private fun readPipes(vertx: Vertx, files: List<String>) = files.forEach {
        val pipe = Json.decodeValue(vertx.fileSystem().readFileBlocking(it), Pipe::class.java)
        try {
            validatePipe(pipe)
            pipes[pipe.header.name] = pipe
        } catch (e: ValidationException) {
            log.warn("Reading $pipe as pipe", e)
        }
    }

}
