package pl.restrictor.kartka.data

import android.content.ContentResolver
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

object DeckFiles {
    const val MAX_BYTES = 20 * 1024 * 1024

    fun read(resolver: ContentResolver, uri: Uri): String {
        resolver.openInputStream(uri).use { input ->
            if (input == null) error("Could not open that file.")
            val buffer = ByteArrayOutputStream()
            val chunk = ByteArray(8192)
            var total = 0
            while (true) {
                val read = input.read(chunk)
                if (read < 0) break
                total += read
                if (total > MAX_BYTES) error("That file is larger than 20 MB.")
                buffer.write(chunk, 0, read)
            }
            val decoder = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            try {
                return decoder.decode(ByteBuffer.wrap(buffer.toByteArray())).toString()
            } catch (_: CharacterCodingException) {
                error("That file is not valid UTF-8.")
            }
        }
    }

    fun write(resolver: ContentResolver, uri: Uri, text: String) {
        resolver.openOutputStream(uri)?.use { output ->
            output.write(text.toByteArray(Charsets.UTF_8))
        } ?: error("Could not save that file.")
    }
}
