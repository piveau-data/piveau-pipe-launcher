package io.piveau.pipe

import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory

class ServiceDiscovery(private val config: JsonObject) {
    private val log = LoggerFactory.getLogger(this.javaClass)

    init {
        if (config.isEmpty) log.warn("Empty service discovery configured!")
    }

    fun resolve(name: String): JsonObject =
        config.getJsonObject(name, JsonObject()).getJsonObject("endpoints", JsonObject())

    fun resolve(name: String, type: String): JsonObject =
        config.getJsonObject(name, JsonObject()).getJsonObject("endpoints", JsonObject()).getJsonObject(
            type,
            JsonObject()
        )
}
