"""Regression coverage for upstream JVM runtime declaration adaptation."""
import unittest

from generate_kotlin import merge_jvm_byte_array


class JvmAdaptationTest(unittest.TestCase):
    def test_multiline_expression_is_preserved_between_declarations(self):
        common = (
            "package test\n"
            "internal expect inline fun ByteArray.getUInt(index: Int): UInt\n"
            "public expect inline fun ByteArray.setInt(index: Int, value: Int)\n"
        )
        jvm = (
            "public actual inline fun ByteArray.getUInt(index: Int): UInt =\n"
            "  ByteArrayOps.getUInt(this, index)\n\n"
            "public actual inline fun ByteArray.setInt(index: Int, value: Int): Unit =\n"
            "  ByteArrayOps.setInt(this, index, value)\n"
        )
        merged = merge_jvm_byte_array(common, jvm)
        self.assertIn("UInt =\n  ByteArrayOps.getUInt(this, index)", merged)
        self.assertIn("Unit =\n  ByteArrayOps.setInt(this, index, value)", merged)
        self.assertNotIn(" actual ", merged)
        self.assertNotIn(" expect ", merged)

    def test_missing_actual_fails_closed(self):
        with self.assertRaisesRegex(RuntimeError, "Missing"):
            merge_jvm_byte_array(
                "internal expect inline fun ByteArray.getInt(index: Int): Int", "",
            )

    def test_unused_actual_fails_closed(self):
        with self.assertRaisesRegex(RuntimeError, "Unmatched"):
            merge_jvm_byte_array(
                "", "public actual inline fun ByteArray.getInt(index: Int): Int = 0",
            )


if __name__ == "__main__":
    unittest.main()
