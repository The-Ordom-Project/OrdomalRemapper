package com.github.ordom.remapper.transformer

import com.github.ordom.remapper.mapping.IntermediaryToSrg
import net.fabricmc.mapping.tree.TinyMappingFactory
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Path

class BytecodeRemapperTest {
    @Test
    fun remap() {
        val i2s = IntermediaryToSrg(Path.of("run"), "1.19.4")
        val remapper = BytecodeRemapper(i2s)
        remapper.remap(
            outputPath = Path.of("run/out"),
            jarFile = Path.of("run/mods/fabric-mod-test-0.1.jar"),
            deleteTmp = true
        )
    }
}
