package com.d10ng.pcmresample.stream

internal class ByteArrayOutputStream {
    private val buffer = mutableListOf<Byte>()

    fun write(value: Int) {
        buffer.add(value.toByte())
    }

    fun write(data: ByteArray, offset: Int, length: Int) {
        buffer.addAll(data.slice(offset..<offset + length))
    }

    fun toByteArray(): ByteArray {
        return buffer.toByteArray()
    }
}