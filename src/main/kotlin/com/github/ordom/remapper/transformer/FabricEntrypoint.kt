package com.github.ordom.remapper.transformer

class FabricEntrypoint {
    data class FabricModMetadata (
        val id: String = "",
        val version: String = "",
        val environment: String = "",
        val entrypoints: Map<String, List<Entrypoint>> = emptyMap()
    ) {
        data class Entrypoint (
            val value: String = "",
            val adapter: String = ""
        )
    }
}