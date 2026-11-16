package com.ollie.iptv.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ParentalPinHasherTest {
    @Test fun matchesPbkdf2HmacSha256KnownVector() {
        val actual = ParentalPinHasher.hash("password", "salt".toByteArray(), iterations = 1)
        val expected = "120fb6cffcf8b32c43e7225256c4f837a86548c92ccc35480805987cb70be17b"
            .chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        assertArrayEquals(expected, actual)
    }

    @Test fun samePinAndSaltProduceSameHash() {
        val salt = ByteArray(16) { it.toByte() }
        assertArrayEquals(ParentalPinHasher.hash("1234", salt), ParentalPinHasher.hash("1234", salt))
    }

    @Test fun differentSaltProducesDifferentHash() {
        val first = ParentalPinHasher.hash("1234", ByteArray(16) { 1 })
        val second = ParentalPinHasher.hash("1234", ByteArray(16) { 2 })
        assertFalse(first.contentEquals(second))
    }

    @Test fun differentPinProducesDifferentHash() {
        val salt = ByteArray(16) { 7 }
        assertFalse(ParentalPinHasher.hash("1234", salt).contentEquals(ParentalPinHasher.hash("4321", salt)))
    }
}
