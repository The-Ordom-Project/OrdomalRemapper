package com.github.ordom.remapper.mapping

import com.github.ordom.remapper.SEARGE
import com.github.ordom.remapper.mapping.tsrg.TsrgReader
import net.fabricmc.mapping.tree.TinyTree
import org.slf4j.LoggerFactory
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.div

class Srg(
    private val gameDir: Path,
    override val version: String,
): MappingProvider {
    override val LOGGER = LoggerFactory.getLogger("Ordomal SRG Mapping")
    private val metadataUrl = "https://maven.minecraftforge.net/de/oceanlabs/mcp/mcp_config/maven-metadata.xml"
    override val path = gameDir / ".ordomal" / "mappings" / SEARGE / "mcp-config-$version-v2.zip"
    override val url = "https://maven.minecraftforge.net/de/oceanlabs/mcp/mcp_config/$version/mcp_config-$version.zip"
    override val shaUrl = "$url.sha1"

    override fun load(): TinyTree {
        FileSystems.newFileSystem(path, emptyMap<String, Any>()).use {
            val mappings = it.getPath("/config/joined.tsrg")
            return Files.newBufferedReader(mappings).use { reader ->
                TsrgReader(reader).generateTiny()
            }
        }
    }
}
