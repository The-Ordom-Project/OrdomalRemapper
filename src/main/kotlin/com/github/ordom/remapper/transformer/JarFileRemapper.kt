package com.github.ordom.remapper.transformer

import com.github.ordom.remapper.mapping.IntermediaryToSrg
import com.github.ordom.remapper.metadata.FabricMetadata
import net.fabricmc.tinyremapper.TinyRemapper
import java.io.File
import java.io.InputStream
import java.nio.file.Path
import java.util.jar.JarFile
import kotlin.io.path.div

class JarFileRemapper(
    private val jar: File,
    private val gameDir: Path = Path.of("run"),
    private val mcVersion: String = "1.19.4"
) {
    private val jarFile = JarFile(jar)
    val i2s = IntermediaryToSrg(gameDir, mcVersion)
    val nestedJarRemappers = mutableListOf<JarFileRemapper>()
    private val fabricMetadata: FabricMetadata =
        FabricMetadata.fromJson(jarFile.getInputStream(jarFile.getJarEntry("fabric.mod.json"))!!.readText())
    private fun resolveNestedJar(unpackPath: Path) {
        val nestedJars = fabricMetadata.jars
        nestedJars.forEach { nestedJar ->
            val nestedJarFile = unpackPath.resolve(nestedJar.file).toFile()
            jarFile.getJarEntry(nestedJar.file).let { jarEntry ->
                jarFile.getInputStream(jarEntry).use { inputStream ->
                    nestedJarFile.parentFile.mkdirs()
                    nestedJarFile.createNewFile()
                    nestedJarFile.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
            }
            nestedJarRemappers.add(JarFileRemapper(nestedJarFile))
            nestedJarRemappers.forEach { it.resolveNestedJar(unpackPath) }
        }
    }
    fun remap(unpackPath: Path = gameDir / "mods") {
        resolveNestedJar(unpackPath)
        BytecodeRemapper(i2s).remap(unpackPath, jar.toPath(), false)
        nestedJarRemappers.forEach { it.remap(unpackPath) }
    }
}

private fun InputStream.readText(): String {
    return this.bufferedReader().use { it.readText() }
}
