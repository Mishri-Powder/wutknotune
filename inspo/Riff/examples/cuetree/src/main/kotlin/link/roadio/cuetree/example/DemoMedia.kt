// SPDX-License-Identifier: Apache-2.0
package link.roadio.cuetree.example

import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem

internal object DemoMedia {
    fun createIfAbsent(base: Path): Path {
        val marker = base.resolve("cuetree-example-v1")
        val music = base.resolve("music")
        if (Files.exists(marker)) {
            require(Files.readString(marker) == "cuetree-example-v1\n") { "Unrecognized demo directory" }
            require(Files.isDirectory(music)) { "Demo music is missing: $music" }
            return music
        }
        require(!Files.exists(music) && !Files.exists(base.resolve("catalog"))) {
            "Demo refuses to replace existing music/catalog directories; choose a new directory."
        }
        val album = Files.createDirectories(music.resolve("Album"))
        writeSilence(album.resolve("disc.wav"), seconds = 2)
        writeSilence(music.resolve("single.wav"), seconds = 1)
        Files.writeString(
            album.resolve("disc.cue"),
            """
            PERFORMER "Example Artist"
            TITLE "Cuetree Demo"
            REM GENRE "Electronic"
            FILE "disc.wav" WAVE
              TRACK 01 AUDIO
                TITLE "First section"
                INDEX 01 00:00:00
              TRACK 02 AUDIO
                TITLE "Second section"
                INDEX 01 00:01:00
            """.trimIndent() + "\n",
        )
        Files.writeString(marker, "cuetree-example-v1\n")
        return music
    }

    private fun writeSilence(
        path: Path,
        seconds: Int,
    ) {
        val format = AudioFormat(8_000f, 16, 1, true, false)
        val frames = 8_000L * seconds
        AudioInputStream(ByteArrayInputStream(ByteArray((frames * format.frameSize).toInt())), format, frames).use {
            AudioSystem.write(it, AudioFileFormat.Type.WAVE, path.toFile())
        }
    }
}
