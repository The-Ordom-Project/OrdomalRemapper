package com.github.ordom.remapper.mapping

import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.nio.file.Path

class IntermediaryToSrgTest {
    val LOGGER = LoggerFactory.getLogger("I2STest")
    @Test
    fun merge() {
        val i2s = IntermediaryToSrg(Intermediary(Path.of("run"), "1.19.4"), Srg(Path.of("run"), "1.19.4"))
        val tree = i2s.merge()
        for (i in 0..100) {
            tree.classes.randomOrNull()?.methods?.randomOrNull()?.let {
                LOGGER.info(it.getDescriptor("srg"))
            }
        }
    }
}
