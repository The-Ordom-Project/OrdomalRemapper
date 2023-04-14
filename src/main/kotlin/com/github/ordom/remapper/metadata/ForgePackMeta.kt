package com.github.ordom.remapper.metadata

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class ForgePackMeta(val pack: PackMeta) {
    @Serializable
    data class PackMeta(
        val description: String,
        @SerialName("pack_format")
        val packFormat: Int,
        @SerialName("forge:resource_pack_format")
        val resourcePackFormat: Int,
        @SerialName("forge:data_pack_format")
        val dataPackFormat: Int
    ) {
        companion object {
            val MC1194 = PackMeta(
                description = "Minecraft 1.19.4",
                packFormat = 12,
                resourcePackFormat = 12,
                dataPackFormat = 10
            )
            // todo: other versions
        }
    }

    override fun toString(): String {
        return Json.encodeToString(this)
    }
}