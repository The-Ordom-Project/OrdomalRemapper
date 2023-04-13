package com.github.ordom.remapper.transformer

import com.github.ordom.remapper.mapping.IntermediaryToSrg
import net.fabricmc.loom.util.TinyRemapperMappingsHelper
import net.fabricmc.tinyremapper.OutputConsumerPath
import net.fabricmc.tinyremapper.TinyRemapper
import java.nio.file.Path

class BytecodeRemapper(
    i2s: IntermediaryToSrg
) {
    private val mapping = i2s.merge()
    val remapper = TinyRemapper.newRemapper()
        .withMappings(TinyRemapperMappingsHelper.create(mapping, "intermediary", "srg", false))
        .build()

    fun remap(jarFile: Path) {
        val output = OutputConsumerPath.Builder(Path.of("out")).build()
        remapper.readInputs(jarFile)
        remapper.apply(output)
    }
}
