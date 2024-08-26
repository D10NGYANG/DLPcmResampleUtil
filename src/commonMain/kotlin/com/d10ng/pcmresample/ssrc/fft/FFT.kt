package com.d10ng.pcmresample.ssrc.fft

interface FFT {
    fun init(n: Int)
    fun reset()
    fun realDFT(a: DoubleArray)
    fun realInverseDFT(a: DoubleArray)
}