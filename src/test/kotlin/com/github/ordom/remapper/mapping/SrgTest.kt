package com.github.ordom.remapper.mapping

import org.junit.jupiter.api.Test
import java.nio.file.Path

class SrgTest {
    @Test
    fun load() {
        val srg = Srg(Path.of("run"), "1.19.4")
        srg.download()
        srg.load()
    }
}
