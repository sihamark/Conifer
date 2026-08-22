package eu.heha.conifer.sync

import kotlin.test.Test
import kotlin.test.assertEquals

class Sha256Test {

    @Test
    fun hashesTheEmptyString() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            "".encodeToByteArray().sha256Hex()
        )
    }

    @Test
    fun hashesTheStandardAbcVector() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            "abc".encodeToByteArray().sha256Hex()
        )
    }

    @Test
    fun hashesAMessageLongerThanOneBlock() {
        assertEquals(
            "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1",
            ("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq")
                .encodeToByteArray().sha256Hex()
        )
    }
}
