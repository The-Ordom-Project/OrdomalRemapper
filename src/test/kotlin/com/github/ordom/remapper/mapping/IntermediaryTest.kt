package com.github.ordom.remapper.mapping

import org.junit.jupiter.api.Test
import java.nio.file.Path

class IntermediaryTest {
    @Test
    fun load() {
        val intermediary = Intermediary(Path.of("run"), "1.19.4")
        intermediary.download()
        intermediary.load()
    }
}
