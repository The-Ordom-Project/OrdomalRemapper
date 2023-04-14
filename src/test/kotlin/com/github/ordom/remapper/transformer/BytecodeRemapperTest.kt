package com.github.ordom.remapper.transformer

import com.github.ordom.remapper.mapping.IntermediaryToSrg
import org.junit.jupiter.api.Test
import java.nio.file.Path

class BytecodeRemapperTest {
    @Test
    fun remap() {
        val i2s = IntermediaryToSrg(Path.of("run"), "1.19.4")
        val remapper = BytecodeRemapper(i2s)
        remapper.remap(Path.of("run/out"), Path.of("run/mods/fabric-mod-test-0.1.jar"))
    }
}
