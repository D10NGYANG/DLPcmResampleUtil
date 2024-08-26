package com.d10ng.pcmresample.stream

internal class ByteArrayInputStream(private val data: ByteArray) {
    private var position = 0

    fun read(): Int {
        return if (position < data.size) {
            data[position++].toInt() and 0xFF
        } else {
            -1
        }
    }

    fun read(buffer: ByteArray, offset: Int = 0, length: Int = buffer.size): Int {
        if (position >= data.size) {
            return -1
        }
        val bytesToRead = minOf(length, data.size - position)
        data.copyInto(buffer, offset, position, position + bytesToRead)
        position += bytesToRead
        return bytesToRead
    }
}