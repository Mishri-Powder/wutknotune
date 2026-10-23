package link.roadio.riff.indexer.android.level2

import com.dynatrace.hash4j.hashing.Hashing
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

class ModelRuntimeInventoryTest {
    @Test fun packagedModelMembersMatchXxh3Inventory() {
        val root = File("src/main/assets/level2")
        val inventory = JSONObject(File(root, "XXH3SUMS.json").readText())
        assertFalse(File(root, "SHA256SUMS.json").exists())
        val names = inventory.keys().asSequence().toList()
        assertEquals(6, names.size)
        for (name in names) {
            val item = inventory.getJSONObject(name)
            val bytes = File(root, name).readBytes()
            val digest = Hashing.xxh3_64().hashBytesToLong(bytes)
            val unsignedHex = java.lang.Long.toUnsignedString(digest, 16)
            assertEquals(bytes.size.toLong(), item.getLong("bytes"))
            assertEquals(unsignedHex.padStart(16, '0'), item.getString("xxh3"))
        }
    }
}
