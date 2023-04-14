package com.github.ordom.remapper.mapping

import com.github.ordom.remapper.INTERMEDIARY
import net.fabricmc.mapping.tree.TinyMappingFactory
import net.fabricmc.mapping.tree.TinyTree
import org.slf4j.LoggerFactory
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.div



/**
 * Mapping from obfuscated names to intermediary names
 */
class Intermediary(
    private val gameDir: Path,
    override val version: String
): MappingProvider
{
    override val LOGGER = LoggerFactory.getLogger("Ordomal Intermediary Mapping")
    override val path = gameDir / ".ordomal" / "mappings" / INTERMEDIARY / "intermediary-$version-v2.jar"
    override val url = "https://maven.fabricmc.net/net/fabricmc/intermediary/$version/intermediary-$version-v2.jar"
    override val shaUrl = "$url.sha1"
    override fun load(): TinyTree {
        FileSystems.newFileSystem(path, emptyMap<String, Any>()).use {
            val mappings = it.getPath("/mappings/mappings.tiny")
            return Files.newBufferedReader(mappings).use { reader ->
                TinyMappingFactory.loadWithDetection(reader, true)
            }
        }
    }
}