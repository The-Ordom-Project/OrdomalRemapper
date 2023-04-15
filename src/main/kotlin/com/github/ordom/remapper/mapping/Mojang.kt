package com.github.ordom.remapper.mapping

import com.github.ordom.remapper.MOJANG
import com.github.ordom.remapper.mapping.tsrg.TSRG2TINY_METADATA
import com.github.ordom.remapper.mapping.tsrg.TsrgTree
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import net.fabricmc.mapping.tree.TinyTree
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path
import kotlin.io.path.*

private val JSON = Json {
    ignoreUnknownKeys = true
    prettyPrint = true
}

/**
 * Used for mapping srg classes to mojang classes, will not map fields or methods
 */
class Mojang(
    override val path: Path,
    override val version: String
) : MappingProvider {
    override val LOGGER = LoggerFactory.getLogger("Ordomal Mojang Mapping")
    override var url: String = ""
    override val shaUrl: String? = null
    private val metadataUrl = "https://launchermeta.mojang.com/mc/game/version_manifest.json"
    private val clientMapPath = path / ".ordomal" / "mappings" / MOJANG / "mojang-$version.txt"
    internal val clientPath = path / ".ordomal" / "jars" / MOJANG / "minecraft-$version-client.jar"
    internal val mappedClientPath = clientPath.resolveSibling("minecraft-${version}-client-mapped")

    /**
     * Only loads class mappings
     */
    override fun load(): TinyTree {
        val reader = clientMapPath.bufferedReader()
        return TsrgTree(TSRG2TINY_METADATA).apply {
            while (reader.ready()) {
                val line = reader.readLine()
                if (line.startsWith("#")) continue
                if (line.firstOrNull() != ' ' || line.endsWith(':')) { // class
                    val parts = line.dropLast(1).split("->")
                        .map { it.trim().replace('.', '/') }
                    if (parts.size != 2) {
                        error("Invalid class mapping: $line")
                    }
                    val classDef = TsrgTree.ClassImpl(arrayOf(parts[1], parts[0]))
                    classes.add(classDef)
                    defaultNamespaceClassMap[parts[1]] = classDef
                }
            }
        }
    }

    @Serializable
    class MCVersionFile(
        val latest: LatestInfo,
        val versions: List<VersionInfo>
    ) {
        @Serializable
        class VersionInfo(
            val id: String,
            val type: String,
            val url: String,
            val time: String,
            val releaseTime: String
        )

        @Serializable
        class LatestInfo(
            val release: String,
            val snapshot: String
        )
    }

    @Serializable
    class VersionFile(
        val downloads: DownloadInfo
    ) {
        @Serializable
        class DownloadInfo(
            val client: DownloadItem,
            val server: DownloadItem,
            @SerialName("client_mappings")
            val clientMappings: DownloadItem,
            @SerialName("server_mappings")
            val serverMappings: DownloadItem
        ) {
            @Serializable
            class DownloadItem(
                val sha1: String,
                val size: Int,
                val url: String
            )
        }
    }

    override fun download() {
        val clientMapExist = clientMapPath.exists()
        val clientExist = clientPath.exists() || mappedClientPath.exists()
        if (!clientMapExist || !clientExist) {
            LOGGER.info("Downloading mojang mappings for version $version")
            val metadata = JSON.decodeFromString<MCVersionFile>(
                httpClient.send(
                    HttpRequest.newBuilder()
                        .uri(URI.create(metadataUrl))
                        .build(),
                    HttpResponse.BodyHandlers.ofString()
                ).body()
            )
            val versionInfo = metadata.versions.first { it.id == version }
            val versionFile = JSON.decodeFromString<VersionFile>(
                httpClient.send(
                    HttpRequest.newBuilder()
                        .uri(URI.create(versionInfo.url))
                        .build(),
                    HttpResponse.BodyHandlers.ofString()
                ).body()
            )
            clientMapPath.parent.createDirectories()
            if (!clientMapExist) {
                LOGGER.info("Downloading client mappings: ${versionFile.downloads.clientMappings.url}")
                clientMapPath.createFile()
                httpClient.send(
                    HttpRequest.newBuilder()
                        .uri(URI.create(versionFile.downloads.clientMappings.url))
                        .build(),
                    HttpResponse.BodyHandlers.ofFile(clientMapPath)
                ).body()
            }
            if (!clientExist) {
                LOGGER.info("Downloading client jar: ${versionFile.downloads.client.url}")
                clientPath.createFile()
                httpClient.send(
                    HttpRequest.newBuilder()
                        .uri(URI.create(versionFile.downloads.client.url))
                        .build(),
                    HttpResponse.BodyHandlers.ofFile(clientPath)
                ).body()
            }
        }
    }
}