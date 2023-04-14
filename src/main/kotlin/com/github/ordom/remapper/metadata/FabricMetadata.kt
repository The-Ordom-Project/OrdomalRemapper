package com.github.ordom.remapper.metadata

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.*
import kotlinx.serialization.json.Json
import java.nio.file.Path

private val JSON = Json {
    ignoreUnknownKeys = true
}

@Serializable
data class FabricMetadata(
    val schemaVersion: Int,
    val id: String,
    val name: String,
    val version: String,
    val entrypoints: Map<String, List<FabricEntrypoint>>,
    val mixins: List<String>
) {
    @Serializable
    data class MixinConfig(
        val mixins: List<String> = emptyList(),
        val client: List<String> = emptyList(),
        val server: List<String> = emptyList(),
        @SerialName("refmap")
        val refMap: String = ""
    ) {
        companion object {
            fun fromJson(json: String): MixinConfig {
                return JSON.decodeFromString(serializer(), json)
            }

            fun fromJson(json: Path): MixinConfig {
                return fromJson(json.toFile().readText())
            }
        }
    }

    companion object {
        fun fromJson(json: String): FabricMetadata {
            return JSON.decodeFromString(serializer(), json)
        }

        fun fromJson(json: Path): FabricMetadata {
            return fromJson(json.toFile().readText())
        }
    }

    @Serializable(with = FabricEntrypoint.Serializer::class)
    data class FabricEntrypoint (
        val value: String = "",
        val adapter: String = ""
    ) {
        object Serializer : KSerializer<FabricEntrypoint> {
            override val descriptor: SerialDescriptor =
                buildClassSerialDescriptor("FabricEntrypoint") {
                    element<String>("value")
                    element<String>("adapter")
                }

            override fun serialize(encoder: Encoder, value: FabricEntrypoint) {
                if (value.adapter != "") {
                    encoder.encodeStructure(descriptor) {
                        encodeStringElement(descriptor, 0, value.value)
                        encodeStringElement(descriptor, 1, value.adapter)
                    }
                }
                else {
                    encoder.encodeString(value.value)
                }
            }

            override fun deserialize(decoder: Decoder): FabricEntrypoint = try {
                // if only a string is present, it is the value
                val decodeString = decoder.decodeString()
                FabricEntrypoint(decodeString)
            } catch (e: Exception) {
                // if a structure is present, it is the value and adapter
                decoder.decodeStructure(descriptor) {
                    var value = ""
                    var adapter = ""
                    while (true) {
                        when (val index = decodeElementIndex(descriptor)) {
                            0 -> value = decodeStringElement(descriptor, 0)
                            1 -> adapter = decodeStringElement(descriptor, 1)
                            CompositeDecoder.DECODE_DONE -> break
                            else -> error("Unexpected index: $index")
                        }
                    }
                    FabricEntrypoint(value, adapter)
                }
            }
        }
    }
}