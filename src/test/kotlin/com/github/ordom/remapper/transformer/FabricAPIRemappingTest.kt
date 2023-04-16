package com.github.ordom.remapper.transformer

import com.github.ordom.remapper.mapping.IntermediaryToSrg
import com.github.ordom.remapper.mapping.MappingProvider
import org.junit.jupiter.api.Test
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse.BodyHandlers
import java.nio.file.Path
import kotlin.io.path.exists

class FabricAPIRemappingTest {
    private val downloadUrl = "https://cdn-raw.modrinth.com/data/P7dR8mSH/versions/unERf4ZJ/fabric-api-0.78.0%2B1.19.4.jar"
    @Test
    fun testRemapFabricApi() {
        val i2s = IntermediaryToSrg(Path.of("run"), "1.19.4")
        val remapper = BytecodeRemapper(i2s)
        val downloadPath = Path.of("run/mods/fabric-api-0.78.0+1.19.4.jar")
        if (!downloadPath.exists()) {
            HttpClient.newHttpClient().send(
                HttpRequest.newBuilder().uri(URI(downloadUrl)).build(),
                BodyHandlers.ofFile(downloadPath)
            )
        }
        val jarFileRemapper = JarFileRemapper(
            jar = downloadPath.toFile(),
            mcVersion = "1.19.4"
        )
        jarFileRemapper.remap()
    }
}
