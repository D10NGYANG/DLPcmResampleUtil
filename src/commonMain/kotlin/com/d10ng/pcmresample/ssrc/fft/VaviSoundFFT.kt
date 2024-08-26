package com.d10ng.pcmresample.ssrc.fft

import com.d10ng.pcmresample.utils.SplitRadixFft
import kotlin.math.sqrt

/**
 * Created by Wayne on 5/19/2015.
 * VaviSoundFFT
 */
class VaviSoundFFT : FFT {
    private var ip: IntArray = IntArray(0)
    private var w: DoubleArray = DoubleArray(0)
    private var n = 0
    private var fft: SplitRadixFft? = null

    override fun init(n: Int) {
        this.n = n
        this.ip = IntArray((2 + sqrt(n.toDouble())).toInt())
        this.w = DoubleArray(n / 2)
        this.fft = SplitRadixFft()
        reset()
    }

    override fun reset() {
        ip[0] = 0
    }

    override fun realDFT(a: DoubleArray) {
        fft!!.rdft(this.n, 1, a, this.ip, this.w)
    }

    override fun realInverseDFT(a: DoubleArray) {
        fft!!.rdft(this.n, -1, a, this.ip, this.w)
    }
}
