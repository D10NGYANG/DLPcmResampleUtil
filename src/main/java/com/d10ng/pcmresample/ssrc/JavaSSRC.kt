/**
 * *****************************************************************************
 * Copyright (c) 2013 Wayne Tam. All rights reserved. This program (except for
 * SplitRadixFft and I0Bessel) is made available under the terms of the GNU
 * Lesser Public License v2.1 which accompanies this distribution, and is
 * available at http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 *
 * Please the source files of SplitRadixFft and I0Bessel for their respective
 * licenses.
 *
 * Contributors: Wayne Tam - initial API and implementation
 * ****************************************************************************
 */
package com.d10ng.pcmresample.ssrc

import com.d10ng.pcmresample.ssrc.fft.FFT
import com.d10ng.pcmresample.ssrc.fft.VaviSoundFFT
import com.d10ng.pcmresample.utils.I0Bessel.value
//import java.io.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*
import kotlin.random.Random

class JavaSSRC {

    interface ProgressListener {
        //fun onChanged(progress: Double)
        fun onShowMessage(message: String?)
    }

    private var listener: ProgressListener? = null
    private var srcChannels = 2
    private var dstChannels = 2
    private var monoChannel = -1
    private var srcByteOrder: ByteOrder = ByteOrder.LITTLE_ENDIAN
    private var srcBPS = 16
    private var dstBPS = 16
    private var srcSamplingRate = 44100
    private var dstSamplingRate = 44100
    private var gain = 1.0
    private var ditherType = 0
    private var pdfType = 0
    private var noiseAmplitude = 0.18
    private var twoPass = false
    private var normalize = false
    private var fast = false
    private var srcFloat = false
    private var dstFloat = false
    private var tempFilename: String? = null
    private var fft: FFT = VaviSoundFFT()

    private var rCtx: ResampleContext? = null

    private class ResampleContext {
        var rnch: Int = 2
        var mono: Int = -1
        var nch: Int = 2
        var dnch: Int = 2
        var bps: Int = 16
        var dbps: Int = 16
        var sfrq: Int = 44100
        var dfrq: Int = 44100
        var gain: Double = 1.0
        var dither: Int = 0
        var pdf: Int = 0
        var noiseamp: Double = 0.18
        var twopass: Boolean = false
        var normalize: Boolean = false
        var srcFloat: Boolean = false
        var dstFloat: Boolean = false
        var AA: Double = 170.0 /* stop band attenuation(dB) */

        var DF: Double = 100.0
        var FFTFIRLEN: Int = 65536
        var ditherSample: Int = 0
        var srcByteOrder: ByteOrder = ByteOrder.LITTLE_ENDIAN
        var tmpFn: String? = null
        var fft: FFT? = null
        var shapebuf: Array<DoubleArray> = emptyArray()
        var shaper_type: Int = 0
        var shaper_len: Int = 0
        var shaper_clipmin: Int = 0
        var shaper_clipmax: Int = 0
        var randbuf: DoubleArray = doubleArrayOf()
        var randptr: Int = 0
        var outBytes: ByteArray? = null

        var bpf: Int = 0
        var wbpf: Int = 0
        var sfrqfrqgcd: Int = 0
        var fs1sfrq: Int = 0
        var fs2fs1: Int = 0
        var fs2dfrq: Int = 0

        var osf: Int = 0
        var fs1: Int = 0
        var fs2: Int = 0
        var n1: Int = 0
        var n2: Int = 0
        var nx: Int = 0
        var ny: Int = 0
        var nb: Int = 0
        var nb2: Int = 0
        var fOrder: IntArray = intArrayOf()
        var fInc: IntArray = intArrayOf()
        var stageA: DoubleArray = doubleArrayOf()
        var stageB: Array<DoubleArray> = emptyArray()
        var rawinbuf: ByteArray = byteArrayOf()
        var rawoutbuf: ByteArray = byteArrayOf()
        var inBuffer: ByteBuffer? = null
        var outBuffer: ByteBuffer? = null
        var inbuf: DoubleArray = doubleArrayOf()
        var outbuf: DoubleArray = doubleArrayOf()
        var buf1: Array<DoubleArray> = emptyArray()
        var buf2: Array<DoubleArray> = emptyArray()
        var frqgcd: Int = 0
        var ip: Int = 0
        var inbuflen: Int = 0
        var sp: Int = 0
        var rps: Int = 0
        var ds: Int = 0
        var rp: Int = 0
        var delay: Int = 0
        var osc: Int = 0
        var peak: Double = 0.0
        var sumread: Long = 0
        var sumwrite: Long = 0
        var init: Boolean = true
    }

    val outBytes: ByteArray
        get() = rCtx!!.outBytes!!

    /*val peak: Double
        get() {
            if (rCtx == null) {
                return 0.0
            }
            return rCtx!!.peak
        }*/

   /* fun setOnProgressListener(listener: ProgressListener?) {
        this.listener = listener
    }*/

    /*fun setFastProfile(fast: Boolean) {
        this.fast = fast
    }*/

    fun setSrcChannels(numChannels: Int) {
        this.srcChannels = numChannels
    }

    fun setDstChannels(numChannels: Int) {
        this.dstChannels = numChannels
    }

    fun setMonoChannel(channel: Int) {
        this.monoChannel = channel
    }

    fun setSrcByteOrder(bo: ByteOrder) {
        this.srcByteOrder = bo
    }

    fun setSrcBPS(srcBPS: Int) {
        require(!(srcBPS != 8 && srcBPS != 16 && srcBPS != 24 && srcBPS != 32)) { "Src BPS type must be 8, 16, 24, or 32 bits (input: $srcBPS)" }
        this.srcBPS = srcBPS
    }

    fun setDstBPS(dstBPS: Int) {
        require(!(dstBPS != 8 && dstBPS != 16 && dstBPS != 24)) { "Dst BPS type must be 8, 16, or 24 bits (input: $dstBPS)" }
        this.dstBPS = dstBPS
    }

    fun setSrcSamplingRate(srcSamplingRate: Int) {
        this.srcSamplingRate = srcSamplingRate
    }

    fun setDstSamplingRate(dstSamplingRate: Int) {
        this.dstSamplingRate = dstSamplingRate
    }

    /*fun setAttenuation(attenuation: Double) {
        this.gain = dBToGain(-attenuation)
    }*/

    /*fun setGain(gain: Double) {
        this.gain = gain
    }*/

    /*fun setDitherType(ditherType: Int) {
        require(!(ditherType < 0 || ditherType > 4)) { "Dither type must be 0, 1, 2, 3, or 4" }
        this.ditherType = ditherType
    }*/

    /*fun setPdfType(pdfType: Int) {
        require(!(pdfType < 0 || pdfType > 2)) { "PDF type must be 0, 1, or 2" }
        this.pdfType = pdfType
        this.noiseAmplitude = noiseAmpPresets[pdfType]
    }*/

    /*fun setNoiseAmplitude(noiseAmplitude: Double) {
        this.noiseAmplitude = noiseAmplitude
    }*/

    /*fun isTwoPass(): Boolean {
        if (rCtx != null) {
            return rCtx!!.twopass
        }
        return this.twoPass
    }*/

    /*fun setTwoPass(twoPass: Boolean) {
        this.twoPass = twoPass
    }*/

    /*fun setNormalize(normalize: Boolean) {
        this.normalize = normalize
    }*/

    /*fun setSrcFloat(srcFloat: Boolean) {
        this.srcFloat = srcFloat
    }*/

    /*fun isSrcFloat(): Boolean {
        if (rCtx != null) {
            return rCtx!!.srcFloat
        }
        return this.srcFloat
    }*/

    /*fun setDstFloat(dstFloat: Boolean) {
        this.dstFloat = dstFloat
    }*/

    /*fun isDstFloat(): Boolean {
        if (rCtx != null) {
            return rCtx!!.dstFloat
        }
        return this.dstFloat
    }*/

    /*fun setTempFilename(tempFilename: String?) {
        this.tempFilename = tempFilename
    }*/

    /*fun setFFT(fft: FFT) {
        this.fft = fft
    }*/

    fun initialize() {
        rCtx = ResampleContext()
        rCtx!!.fft = fft
        rCtx!!.rnch = srcChannels
        rCtx!!.mono = if (monoChannel < srcChannels) monoChannel else srcChannels - 1
        if (rCtx!!.rnch > 1 && rCtx!!.mono > -1) {
            rCtx!!.nch = 1
        } else {
            rCtx!!.nch = rCtx!!.rnch
        }
        rCtx!!.dnch = dstChannels
        rCtx!!.srcByteOrder = srcByteOrder
        rCtx!!.bps = srcBPS / 8
        rCtx!!.dbps = dstBPS / 8
        rCtx!!.sfrq = srcSamplingRate
        rCtx!!.dfrq = dstSamplingRate
        rCtx!!.gain = gain
        rCtx!!.srcFloat = srcFloat
        if (rCtx!!.srcFloat) {
            rCtx!!.bps = 4
        }
        rCtx!!.dstFloat = dstFloat
        if (rCtx!!.dstFloat) {
            rCtx!!.dbps = 3
        }
        if (rCtx!!.bps == rCtx!!.dbps || rCtx!!.srcFloat == rCtx!!.dstFloat) {
            rCtx!!.dither = 0
        } else {
            rCtx!!.dither = ditherType
        }
        rCtx!!.pdf = pdfType
        rCtx!!.noiseamp = noiseAmplitude
        rCtx!!.normalize = normalize
        rCtx!!.twopass =
            !(rCtx!!.sfrq == rCtx!!.dfrq && rCtx!!.dither == 0 && rCtx!!.gain == 1.0) && (rCtx!!.normalize || twoPass)
        rCtx!!.tmpFn = tempFilename
        if (fast) {
            rCtx!!.AA = 96.0
            rCtx!!.DF = 8000.0
            rCtx!!.FFTFIRLEN = 1024
        } else {
            rCtx!!.AA = 170.0
            rCtx!!.DF = 100.0
            rCtx!!.FFTFIRLEN = 65536
        }

        if (rCtx!!.dither > 0) {
            init_shaper()
        }

        rCtx!!.bpf = rCtx!!.bps * rCtx!!.rnch
        rCtx!!.wbpf = rCtx!!.bps * (if (rCtx!!.twopass) rCtx!!.nch else rCtx!!.dnch)
        if (rCtx!!.sfrq < rCtx!!.dfrq) {
            initUpSample(rCtx!!)
        } else if (rCtx!!.sfrq > rCtx!!.dfrq) {
            initDownSample(rCtx!!)
        } else {
            val outBps = if (rCtx!!.dstFloat) 4 else rCtx!!.dbps
            rCtx!!.rawinbuf = ByteArray(16384 * rCtx!!.bpf)
            if (rCtx!!.twopass) {
                rCtx!!.rawoutbuf = ByteArray(16384 * 8 * rCtx!!.nch)
            } else {
//                rCtx.rawoutbuf = new byte[(int) (16384 * outBps * rCtx.dnch * ((double) outBps / rCtx.bps))];
                rCtx!!.rawoutbuf = ByteArray(16384 * outBps * rCtx!!.dnch)
            }
            rCtx!!.inBuffer = ByteBuffer.wrap(rCtx!!.rawinbuf).order(rCtx!!.srcByteOrder)
            rCtx!!.outBuffer = ByteBuffer.wrap(rCtx!!.rawoutbuf).order(ByteOrder.LITTLE_ENDIAN)
            rCtx!!.outBytes = ByteArray(rCtx!!.rawoutbuf.size)
        }
    }

    private fun init_shaper() {
        var i: Int
        val pool = IntArray(POOLSIZE)

        i = 1
        while (i < 6) {
            if (rCtx!!.dfrq == scoeffreq[i]) {
                break
            }
            i++
        }
        if ((rCtx!!.dither == 3 || rCtx!!.dither == 4) && i == 6) {
            showMessage(
                String.format(
                    "Warning: ATH based noise shaping for destination frequency %dHz is not available, using triangular dither",
                    rCtx!!.dfrq
                )
            )
        }
        if (rCtx!!.dither == 2 || i == 6) {
            i = 0
        }
        if (rCtx!!.dither == 4 && (i == 1 || i == 2)) {
            i += 5
        }

        rCtx!!.shaper_type = i

        rCtx!!.shaper_len = scoeflen[rCtx!!.shaper_type]
        rCtx!!.shapebuf = Array(rCtx!!.nch) { DoubleArray(rCtx!!.shaper_len) }

        if (rCtx!!.dbps == 1) {
            rCtx!!.shaper_clipmin = -0x80
            rCtx!!.shaper_clipmax = 0x7f
        }
        if (rCtx!!.dbps == 2) {
            rCtx!!.shaper_clipmin = -0x8000
            rCtx!!.shaper_clipmax = 0x7fff
        }
        if (rCtx!!.dbps == 3) {
            rCtx!!.shaper_clipmin = -0x800000
            rCtx!!.shaper_clipmax = 0x7fffff
        }
        if (rCtx!!.dbps == 4) {
            rCtx!!.shaper_clipmin = -0x80000000
            rCtx!!.shaper_clipmax = 0x7fffffff
        }

        rCtx!!.randbuf = DoubleArray(RANDBUFLEN)
        val random = Random(System.nanoTime())

        i = 0
        while (i < POOLSIZE) {
            pool[i] = random.nextInt(Int.MAX_VALUE)
            i++
        }

        var r1: Int
        var r2: Int
        var p: Int
        var r: Double

        when (rCtx!!.pdf) {
            0 -> {
                i = 0
                while (i < RANDBUFLEN) {
                    p = random.nextInt(Int.MAX_VALUE) % POOLSIZE
                    r1 = pool[p]
                    pool[p] = random.nextInt(Int.MAX_VALUE)
                    rCtx!!.randbuf[i] = rCtx!!.noiseamp * ((r1.toDouble()) / Int.MAX_VALUE - 0.5)
                    i++
                }
            }

            1 -> {
                i = 0
                while (i < RANDBUFLEN) {
                    p = random.nextInt(Int.MAX_VALUE) % POOLSIZE
                    r1 = pool[p]
                    pool[p] = random.nextInt(Int.MAX_VALUE)
                    p = random.nextInt(Int.MAX_VALUE) % POOLSIZE
                    r2 = pool[p]
                    pool[p] = random.nextInt(Int.MAX_VALUE)
                    rCtx!!.randbuf[i] =
                        rCtx!!.noiseamp * (((r1.toDouble()) / Int.MAX_VALUE) - ((r2.toDouble()) / Int.MAX_VALUE))
                    i++
                }
            }

            2 -> {
                var sw = 0
                var t = 0.0
                var u = 0.0

                i = 0
                while (i < RANDBUFLEN) {
                    if (sw == 0) {
                        sw = 1

                        p = random.nextInt(Int.MAX_VALUE) % POOLSIZE
                        r = (pool[p].toDouble()) / Int.MAX_VALUE
                        pool[p] = random.nextInt(Int.MAX_VALUE)
                        if (r == 1.0) {
                            r = 0.0
                        }

                        t = sqrt(-2 * ln(1 - r))

                        p = random.nextInt(Int.MAX_VALUE) % POOLSIZE
                        r = (pool[p].toDouble()) / Int.MAX_VALUE
                        pool[p] = random.nextInt(Int.MAX_VALUE)

                        u = 2 * Math.PI * r

                        rCtx!!.randbuf[i] = rCtx!!.noiseamp * t * cos(u)
                    } else {
                        sw = 0
                        rCtx!!.randbuf[i] = rCtx!!.noiseamp * t * sin(u)
                    }
                    i++
                }
            }
        }

        rCtx!!.randptr = 0

        if (rCtx!!.dither == 0 || rCtx!!.dither == 1) {
            rCtx!!.ditherSample = 1
        } else {
            rCtx!!.ditherSample = samp[rCtx!!.shaper_type]
        }
    }

    /*private fun showProgress(p: Double) {
        if (listener != null) {
            listener!!.onChanged(p)
        }
    }*/

    private fun showMessage(msg: String) {
        if (listener != null) {
            listener!!.onShowMessage(msg)
        }
    }

    /*private fun upsample(fpi: InputStream, fpo: OutputStream, gain: Double, length: Long) {
        var spcount = 0
        var ending: Boolean
        var nsmplread: Int
        var toberead: Int
        var toberead2: Int
        var tmpLen: Int
        var readLen: Int
        var nsmplwrt1: Int
        var EOF = false
        var nsmplwrt2: Int
        val dbps = if (rCtx!!.twopass) 8 else if (rCtx!!.dstFloat) 4 else rCtx!!.dbps
        val chanklen = length / rCtx!!.bps / rCtx!!.rnch
        val tobereadbase =
            floor(rCtx!!.nb2.toDouble() * rCtx!!.sfrq / (rCtx!!.dfrq * rCtx!!.osf)).toInt() + 1 + rCtx!!.nx
        rCtx!!.sumwrite = 0
        rCtx!!.sumread = rCtx!!.sumwrite

        while (true) {
            toberead = tobereadbase - rCtx!!.inbuflen
            toberead2 = toberead
            if (toberead + rCtx!!.sumread > chanklen) {
                toberead = (chanklen - rCtx!!.sumread).toInt()
            }

            rCtx!!.inBuffer!!.clear()
            nsmplread = 0
            readLen = rCtx!!.bpf * toberead
            try {
                while (nsmplread < readLen && !EOF) {
                    tmpLen = fpi.read(rCtx!!.rawinbuf, nsmplread, readLen - nsmplread)
                    if (tmpLen < 0) {
                        EOF = true
                    } else {
                        nsmplread += tmpLen
                    }
                }
            } catch (e: Exception) {
                EOF = true
            }
            rCtx!!.inBuffer!!.limit(nsmplread)
            nsmplread /= rCtx!!.bpf
            fillInBuf(rCtx!!, nsmplread)
            rCtx!!.inbuf.fill(0.0,rCtx!!.nch * rCtx!!.inbuflen + nsmplread * rCtx!!.nch, rCtx!!.nch * rCtx!!.inbuflen + rCtx!!.nch * toberead2)

            rCtx!!.inbuflen += toberead2

            rCtx!!.sumread += nsmplread.toLong()
            ending = EOF || rCtx!!.sumread >= chanklen

            //nsmplwrt1 = ((rp-1)*srcSamplingRate/fs1+inbuflen-n1x)*dstSamplingRate*osf/srcSamplingRate;
            //if (nsmplwrt1 > n2b2) nsmplwrt1 = n2b2;
            nsmplwrt1 = rCtx!!.nb2

            rCtx!!.ip = ((rCtx!!.sfrq * (rCtx!!.rp - 1) + rCtx!!.fs1) / rCtx!!.fs1) * rCtx!!.nch
            nsmplwrt2 = upSample(rCtx!!, nsmplwrt1)
            rCtx!!.rp += nsmplwrt1 * rCtx!!.sfrqfrqgcd / rCtx!!.osf

            rCtx!!.outBuffer!!.clear()
            fillOutBuf(rCtx!!, dbps, gain, nsmplwrt2)
            rCtx!!.outBuffer!!.flip()

            if (writeOutStream(rCtx!!, fpo, nsmplwrt2, dbps, ending)) {
                break
            }

            rCtx!!.ds = (rCtx!!.rp - 1) / rCtx!!.fs1sfrq
            assert(rCtx!!.inbuflen >= rCtx!!.ds)
            System.arraycopy(
                rCtx!!.inbuf,
                rCtx!!.nch * rCtx!!.ds,
                rCtx!!.inbuf,
                0,
                rCtx!!.nch * (rCtx!!.inbuflen - rCtx!!.ds)
            )
            rCtx!!.inbuflen -= rCtx!!.ds
            rCtx!!.rp -= rCtx!!.ds * rCtx!!.fs1sfrq
            if ((spcount++ and 7) == 7) {
                showProgress(rCtx!!.sumread.toDouble() / chanklen)
            }
        }
        showProgress(1.0)
    }*/

    /*private fun downsample(fpi: InputStream, fpo: OutputStream, gain: Double, length: Long) {
        var spcount = 0
        var nsmplwrt2: Int
        var ending: Boolean
        var ch: Int
        val dbps = if (rCtx!!.twopass) 8 else if (rCtx!!.dstFloat) 4 else rCtx!!.dbps
        val chanklen = length / rCtx!!.bps / rCtx!!.rnch

        rCtx!!.sumwrite = 0
        rCtx!!.sumread = rCtx!!.sumwrite

        var nsmplread: Int
        var toberead: Int
        var readLen: Int
        var tmpLen: Int
        var EOF = false

        while (true) {
            toberead = (rCtx!!.nb2 - rCtx!!.rps - 1) / rCtx!!.osf + 1
            if (toberead + rCtx!!.sumread > chanklen) {
                toberead = (chanklen - rCtx!!.sumread).toInt()
            }

            rCtx!!.inBuffer!!.clear()
            nsmplread = 0
            readLen = rCtx!!.bpf * toberead
            try {
                while (nsmplread < readLen && !EOF) {
                    tmpLen = fpi.read(rCtx!!.rawinbuf, nsmplread, readLen - nsmplread)
                    if (tmpLen < 0) {
                        EOF = true
                    } else {
                        nsmplread += tmpLen
                    }
                }
            } catch (e: Exception) {
                EOF = true
            }
            rCtx!!.inBuffer!!.limit(nsmplread)
            nsmplread /= rCtx!!.bpf
            fillInBuf(rCtx!!, nsmplread)
            rCtx!!.inbuf.fill(0.0,nsmplread * rCtx!!.nch, rCtx!!.nch * toberead)

            rCtx!!.sumread += nsmplread.toLong()
            ending = EOF || rCtx!!.sumread >= chanklen

            nsmplwrt2 = downSample(rCtx!!)

            rCtx!!.rp += nsmplwrt2 * rCtx!!.fs2dfrq

            rCtx!!.outBuffer!!.clear()
            fillOutBuf(rCtx!!, dbps, gain, nsmplwrt2)
            rCtx!!.outBuffer!!.flip()
            if (writeOutStream(rCtx!!, fpo, nsmplwrt2, dbps, ending)) {
                break
            }

            rCtx!!.ds = (rCtx!!.rp - 1) / rCtx!!.fs2fs1
            if (rCtx!!.ds > rCtx!!.nb2) {
                rCtx!!.ds = rCtx!!.nb2
            }
            ch = 0
            while (ch < rCtx!!.nch) {
                System.arraycopy(rCtx!!.buf2[ch], rCtx!!.ds, rCtx!!.buf2[ch], 0, rCtx!!.nx + 1 + rCtx!!.nb2 - rCtx!!.ds)
                ch++
            }
            rCtx!!.rp -= rCtx!!.ds * rCtx!!.fs2fs1
            ch = 0
            while (ch < rCtx!!.nch) {
                System.arraycopy(rCtx!!.buf1[ch], rCtx!!.nb2, rCtx!!.buf2[ch], rCtx!!.nx + 1, rCtx!!.nb2)
                ch++
            }
            if ((spcount++ and 7) == 7) {
                showProgress(rCtx!!.sumread.toDouble() / chanklen)
            }
        }

        showProgress(1.0)
    }*/

    /*private fun no_src(fpi: InputStream, fpo: OutputStream, gain: Double, length: Long) {
        var ch: Int
        var sumread = 0
        var readLen: Int
        var tmpLen: Int
        var f: Double
        var p: Double
        val chunklen = length / rCtx!!.bps / rCtx!!.rnch
        var j = 0
        if (rCtx!!.nch == 1 && rCtx!!.rnch != rCtx!!.nch) {
            j = rCtx!!.mono
        }

        while (sumread < chunklen) {
            try {
                rCtx!!.inBuffer!!.clear()
                readLen = 0
                while (readLen < rCtx!!.bps * rCtx!!.rnch) {
                    tmpLen = fpi.read(rCtx!!.rawinbuf, readLen, rCtx!!.bps * rCtx!!.rnch - readLen)
                    if (tmpLen < 0) {
                        throw Exception("EOF")
                    }
                    readLen += tmpLen
                }
            } catch (ignored: Exception) {
            }
            rCtx!!.outBuffer!!.clear()

            if (rCtx!!.twopass) {
                ch = 0
                while (ch < rCtx!!.nch) {
                    f = readFromInBuffer(rCtx!!, (ch + j) * rCtx!!.bps) * gain
                    p = if (f > 0) f else -f
                    rCtx!!.peak = if (rCtx!!.peak < p) p else rCtx!!.peak
                    rCtx!!.outBuffer!!.putDouble(f)
                    ch++
                }
                fpo.write(rCtx!!.rawoutbuf, 0, 8 * rCtx!!.nch)
            } else {
                ch = 0
                while (ch < rCtx!!.dnch) {
                    f = readFromInBuffer(rCtx!!, (ch % rCtx!!.nch + j) * rCtx!!.bps) * gain
                    writeToOutBuffer(rCtx!!, f, (ch % rCtx!!.nch))
                    ch++
                }
                fpo.write(rCtx!!.rawoutbuf, 0, rCtx!!.dbps * rCtx!!.dnch)
            }

            sumread++

            if ((sumread and 0x3ffff) == 0) {
                showProgress(sumread.toDouble() / chunklen)
            }
        }

        fpo.flush()
        showProgress(1.0)
    }*/

    /*fun resetShaper() {
        rCtx!!.randptr = 0
    }*/

    /*fun doubleToBytes(d: Double, ch: Int, ob: ByteBuffer) {
        var s: Int
        when (rCtx!!.dbps) {
            1 -> {
                s = if (rCtx!!.dither > 0) do_shaping(rCtx!!, d, ch) else RINT(d)
                ob.put((s + 128).toByte())
            }

            2 -> {
                s = if (rCtx!!.dither > 0) do_shaping(rCtx!!, d, ch) else RINT(d)
                ob.putShort(s.toShort())
            }

            3 -> {
                s = if (rCtx!!.dither > 0) do_shaping(rCtx!!, d, ch) else RINT(d)
                if (rCtx!!.dstFloat) {
                    ob.putFloat(((1.0 / 0x7fffff) * s).toFloat())
                } else {
                    ob.putShort(s.toShort())
                    s = s shr 16
                    ob.put(s.toByte())
                }
            }
        }
    }*/

    /*fun calcSecondPassGain(): Double {
        if (!rCtx!!.normalize) {
            if (rCtx!!.peak < rCtx!!.gain) {
                rCtx!!.peak = 1.0
            } else {
                rCtx!!.peak *= 10.0.pow(-log10(rCtx!!.gain))
            }
        } else {
            rCtx!!.peak *= 10.0.pow(-log10(rCtx!!.gain))
        }

        if (rCtx!!.dither > 0) {
            when (rCtx!!.dbps) {
                1 -> return if ((rCtx!!.normalize || rCtx!!.peak >= (0x7f - rCtx!!.ditherSample) / (0x7f).toDouble())) 1 / rCtx!!.peak * (0x7f - rCtx!!.ditherSample) else 1 / rCtx!!.peak * 0x7f
                2 -> return if ((rCtx!!.normalize || rCtx!!.peak >= (0x7fff - rCtx!!.ditherSample) / (0x7fff).toDouble())) 1 / rCtx!!.peak * (0x7fff - rCtx!!.ditherSample) else 1 / rCtx!!.peak * 0x7fff
                3 -> return if ((rCtx!!.normalize || rCtx!!.peak >= (0x7fffff - rCtx!!.ditherSample) / (0x7fffff).toDouble())) 1 / rCtx!!.peak * (0x7fffff - rCtx!!.ditherSample) else 1 / rCtx!!.peak * 0x7fffff
            }
        } else {
            when (rCtx!!.dbps) {
                1 -> return 1 / rCtx!!.peak * 0x7f
                2 -> return 1 / rCtx!!.peak * 0x7fff
                3 -> return 1 / rCtx!!.peak * 0x7fffff
            }
        }
        return 1.0
    }*/

    /*fun resample(samples: Array<FloatArray>, length: Int, isLast: Boolean): Int {
        checkNotNull(rCtx) { "Resampler has not been initialized" }

        check(rCtx!!.srcFloat) { "Source is not set to floating point" }

        return if (rCtx!!.sfrq < rCtx!!.dfrq) {
            upsample(rCtx!!, samples, length, rCtx!!.gain, isLast)
        } else if (rCtx!!.sfrq > rCtx!!.dfrq) {
            downsample(rCtx!!, samples, length, rCtx!!.gain, isLast)
        } else {
            no_src(rCtx!!, samples, length, rCtx!!.gain)
        }
    }*/

    /*fun resample(samples: Array<IntArray>, length: Int, isLast: Boolean): Int {
        checkNotNull(rCtx) { "Resampler has not been initialized" }

        return if (rCtx!!.sfrq < rCtx!!.dfrq) {
            upsample(rCtx!!, samples, length, rCtx!!.gain, isLast)
        } else if (rCtx!!.sfrq > rCtx!!.dfrq) {
            downsample(rCtx!!, samples, length, rCtx!!.gain, isLast)
        } else {
            no_src(rCtx!!, samples, length, rCtx!!.gain)
        }
    }*/

    fun resample(samples: ByteArray, offset: Int, length: Int, isLast: Boolean): Int {
        checkNotNull(rCtx) { "Resampler has not been initialized" }

        return if (rCtx!!.sfrq < rCtx!!.dfrq) {
            upsample(rCtx!!, samples, offset, length, rCtx!!.gain, isLast)
        } else if (rCtx!!.sfrq > rCtx!!.dfrq) {
            downsample(rCtx!!, samples, offset, length, rCtx!!.gain, isLast)
        } else {
            no_src(rCtx!!, samples, offset, length, rCtx!!.gain)
        }
    }

    /*fun resample(fpi: InputStream, fpo: OutputStream, length: Long): Double {
        checkNotNull(rCtx) { "Resampler has not been initialized" }

        if (rCtx!!.twopass) {
            val tmpFile: File
            if (rCtx!!.tmpFn != null) {
                tmpFile = File(rCtx!!.tmpFn!!)
            } else {
                tmpFile = File.createTempFile("JavaSSRC_", null)
                tmpFile.deleteOnExit()
            }
            val fpt = FileOutputStream(tmpFile)

            showMessage("Pass 1")
            try {
                if (rCtx!!.normalize) {
                    if (rCtx!!.sfrq < rCtx!!.dfrq) {
                        upsample(fpi, fpt, 1.0, length)
                    } else if (rCtx!!.sfrq > rCtx!!.dfrq) {
                        downsample(fpi, fpt, 1.0, length)
                    } else {
                        no_src(BufferedInputStream(fpi), BufferedOutputStream(fpt), 1.0, length)
                    }
                } else {
                    if (rCtx!!.sfrq < rCtx!!.dfrq) {
                        upsample(fpi, fpt, rCtx!!.gain, length)
                    } else if (rCtx!!.sfrq > rCtx!!.dfrq) {
                        downsample(fpi, fpt, rCtx!!.gain, length)
                    } else {
                        no_src(BufferedInputStream(fpi), BufferedOutputStream(fpt), rCtx!!.gain, length)
                    }
                }

                fpt.close()
            } catch (e: IOException) {
                throw IOException("Error processing audio data", e)
            }

            showMessage(String.format("\npeak : %gdB", 20 * log10(rCtx!!.peak)))

            showMessage("\nPass 2")

            val secondPassGain = calcSecondPassGain()

            resetShaper()

            val fptlen = tmpFile.length() / (8 * rCtx!!.nch)
            var sumread = 0
            var ch: Int
            var inStrm: DataInputStream? = null
            var outStrm: BufferedOutputStream? = null
            try {
                inStrm = DataInputStream(BufferedInputStream(FileInputStream(tmpFile)))
                outStrm = BufferedOutputStream(fpo)
                val ibuf = ByteArray(8 * rCtx!!.nch)
                val bb = ByteBuffer.wrap(ibuf).order(ByteOrder.LITTLE_ENDIAN)
                val db = bb.asDoubleBuffer()
                var f: Double

                while (true) {
                    try {
                        inStrm.readFully(ibuf)
                    } catch (e: EOFException) {
                        break
                    }
                    bb.clear()
                    ch = 0
                    while (ch < rCtx!!.dnch) {
                        f = db[ch % rCtx!!.nch] * secondPassGain
                        doubleToBytes(f, ch % rCtx!!.nch, bb)
                        ch++
                    }
                    outStrm.write(bb.array(), 0, bb.position())
                    sumread++

                    if ((sumread and 0x3ffff) == 0) {
                        listener!!.onChanged(sumread.toDouble() / fptlen)
                    }
                }
                showProgress(1.0)
            } catch (e1: FileNotFoundException) {
                throw FileNotFoundException("Error opening temp file")
            } catch (e: IOException) {
                throw IOException("Error processing temp file", e)
            } finally {
                try {
                    outStrm?.flush()
                } catch (ignored: IOException) {
                }
                try {
                    inStrm?.close()
                } catch (ignored: IOException) {
                }
                try {
                    tmpFile.delete()
                } catch (e: Exception) {
                    showMessage(String.format("Failed to delete temp file %s", rCtx!!.tmpFn))
                }
            }
        } else {
            val outStrm = BufferedOutputStream(fpo)
            try {
                if (rCtx!!.sfrq < rCtx!!.dfrq) {
                    upsample(fpi, outStrm, rCtx!!.gain, length)
                } else if (rCtx!!.sfrq > rCtx!!.dfrq) {
                    downsample(fpi, outStrm, rCtx!!.gain, length)
                } else {
                    no_src(BufferedInputStream(fpi), outStrm, rCtx!!.gain, length)
                }
            } catch (e: IOException) {
                throw IOException("Error processing audio data", e)
            } finally {
                outStrm.flush()
            }
        }
        return rCtx!!.peak
    }*/

    companion object {
        //	private static final boolean DEBUG = false; 
        //const val VERSION: String = "1.40"
        private const val RANDBUFLEN = 65536

        private val scoeflen = intArrayOf(1, 16, 20, 16, 16, 15, 16, 15)
        private val samp = intArrayOf(8, 18, 27, 8, 8, 8, 10, 9)
        private val scoeffreq = intArrayOf(0, 48000, 44100, 37800, 32000, 22050, 48000, 44100)

        private val shapercoefs = arrayOf(
            doubleArrayOf(-1.0),  /* triangular dither */
            doubleArrayOf(
                -2.8720729351043701172, 5.0413231849670410156, -6.2442994117736816406, 5.8483986854553222656,
                -3.7067542076110839844, 1.0495119094848632812, 1.1830236911773681641, -2.1126792430877685547,
                1.9094531536102294922, -0.99913084506988525391, 0.17090806365013122559, 0.32615602016448974609,
                -0.39127644896507263184, 0.26876461505889892578, -0.097676105797290802002, 0.023473845794796943665,
            ),
            /* 48k, N=16, amp=18 */
            doubleArrayOf(
                -2.6773197650909423828, 4.8308925628662109375, -6.570110321044921875, 7.4572014808654785156,
                -6.7263274192810058594, 4.8481650352478027344, -2.0412089824676513672, -0.7006359100341796875,
                2.9537565708160400391, -4.0800385475158691406, 4.1845216751098632812, -3.3311812877655029297,
                2.1179926395416259766, -0.879302978515625, 0.031759146600961685181, 0.42382788658142089844,
                -0.47882103919982910156, 0.35490813851356506348, -0.17496839165687561035, 0.060908168554306030273,
            ),
            /* 44.1k, N=20, amp=27 */
            doubleArrayOf(
                -1.6335992813110351562, 2.2615492343902587891, -2.4077029228210449219, 2.6341717243194580078,
                -2.1440362930297851562, 1.8153258562088012695, -1.0816224813461303711, 0.70302653312683105469,
                -0.15991993248462677002, -0.041549518704414367676, 0.29416576027870178223, -0.2518316805362701416,
                0.27766478061676025391, -0.15785403549671173096, 0.10165894031524658203, -0.016833892092108726501,
            ),
            /* 37.8k, N=16 */
            doubleArrayOf(
                -0.82901298999786376953, 0.98922657966613769531, -0.59825712442398071289, 1.0028809309005737305,
                -0.59938216209411621094, 0.79502451419830322266, -0.42723315954208374023, 0.54492527246475219727,
                -0.30792605876922607422, 0.36871799826622009277, -0.18792048096656799316, 0.2261127084493637085,
                -0.10573341697454452515, 0.11435490846633911133, -0.038800679147243499756, 0.040842197835445404053,
            ),
            /* 32k, N=16 */
            doubleArrayOf(
                -0.065229974687099456787, 0.54981261491775512695, 0.40278548002243041992, 0.31783768534660339355,
                0.28201797604560852051, 0.16985194385051727295, 0.15433363616466522217, 0.12507140636444091797,
                0.08903945237398147583, 0.064410120248794555664, 0.047146003693342208862, 0.032805237919092178345,
                0.028495194390416145325, 0.011695005930960178375, 0.011831838637590408325,
            ),
            /* 22.05k, N=15 */
            doubleArrayOf(
                -2.3925774097442626953, 3.4350297451019287109, -3.1853709220886230469, 1.8117271661758422852,
                0.20124770700931549072, -1.4759907722473144531, 1.7210904359817504883, -0.97746700048446655273,
                0.13790138065814971924, 0.38185903429985046387, -0.27421241998672485352, -0.066584214568138122559,
                0.35223302245140075684, -0.37672343850135803223, 0.23964276909828186035, -0.068674825131893157959,
            ),
            /* 48k, N=16, amp=10 */
            doubleArrayOf(
                -2.0833916664123535156, 3.0418450832366943359, -3.2047898769378662109, 2.7571926116943359375,
                -1.4978630542755126953, 0.3427594602108001709, 0.71733748912811279297, -1.0737057924270629883,
                1.0225815773010253906, -0.56649994850158691406, 0.20968692004680633545, 0.065378531813621520996,
                -0.10322438180446624756, 0.067442022264003753662, 0.00495197344571352005,
            ),
            /* 44.1k, N=15, amp=9 */
        )

        private fun RINT(x: Double): Int {
            return (if ((x) >= 0) (((x) + 0.5).toInt()) else (((x) - 0.5).toInt()))
        }

        //private val noiseAmpPresets = doubleArrayOf(0.7, 0.9, 0.18)

        private const val POOLSIZE = 97

        private val NORMALIZE_FACTOR_8: Double = 1.0 / 0x7f
        private val NORMALIZE_FACTOR_16: Double = 1.0 / 0x7fff
        private val NORMALIZE_FACTOR_24: Double = 1.0 / 0x7fffff
        private val NORMALIZE_FACTOR_32: Double = 1.0 / 0x7fffffff

        private fun reset(rCtx: ResampleContext) {
            rCtx.init = true
            rCtx.sumwrite = 0
            rCtx.sumread = rCtx.sumwrite
            rCtx.osc = 0
            rCtx.ds = rCtx.osc
            rCtx.rps = rCtx.ds
            rCtx.rp = rCtx.rps
            rCtx.sp = rCtx.rp
            rCtx.peak = 0.0

            rCtx.fft!!.reset()
            rCtx.fft!!.realDFT(rCtx.stageA)

            for (i in 0 until rCtx.nch) {
                rCtx.buf1[i].fill(0.0)
                rCtx.buf2[i].fill(0.0)
            }
            rCtx.inBuffer!!.clear()
            rCtx.outBuffer!!.clear()

            if (rCtx.sfrq < rCtx.dfrq) {
                rCtx.inbuflen = rCtx.n1 / 2 / (rCtx.fs1 / rCtx.sfrq) + 1
                rCtx.delay = (rCtx.n2.toDouble() / 2 / (rCtx.fs2 / rCtx.dfrq)).toInt()
            } else if (rCtx.sfrq > rCtx.dfrq) {
                rCtx.inbuflen = 0
                rCtx.delay =
                    (rCtx.n1.toDouble() / 2 / (rCtx.fs1.toDouble() / rCtx.dfrq) + rCtx.n2.toDouble() / 2 / (rCtx.fs2.toDouble() / rCtx.dfrq)).toInt()
            }
        }

        private fun initUpSample(rCtx: ResampleContext) {
            val filter2len = rCtx.FFTFIRLEN /* stage 2 filter length */

            /* Make stage 1 filter */
            var lpf: Double
            var df: Double
            var iza: Double
            val guard = 2.0
            var i: Int

            rCtx.frqgcd = gcd(rCtx.sfrq, rCtx.dfrq)

            rCtx.fs1 = rCtx.sfrq / rCtx.frqgcd * rCtx.dfrq

            rCtx.sfrqfrqgcd = rCtx.sfrq / rCtx.frqgcd
            rCtx.fs1sfrq = rCtx.fs1 / rCtx.sfrq

            if (rCtx.fs1 / rCtx.dfrq == 1) {
                rCtx.osf = 1
            } else if (rCtx.fs1 / rCtx.dfrq % 2 == 0) {
                rCtx.osf = 2
            } else if (rCtx.fs1 / rCtx.dfrq % 3 == 0) {
                rCtx.osf = 3
            } else {
                throw UnsupportedOperationException(
                    String.format(
                        """
                        Resampling from %dHz to %dHz is not supported.
                        %d/gcd(%d,%d)=%d must be divisible by 2 or 3.
                        """.trimIndent(),
                        rCtx.sfrq, rCtx.dfrq, rCtx.sfrq, rCtx.sfrq, rCtx.dfrq, rCtx.fs1 / rCtx.dfrq
                    )
                )
            }

            df = (rCtx.dfrq * rCtx.osf / 2 - rCtx.sfrq / 2) * 2 / guard
            lpf = rCtx.sfrq / 2 + (rCtx.dfrq * rCtx.osf / 2 - rCtx.sfrq / 2) / guard

            var d = if (rCtx.AA <= 21) {
                0.9222
            } else {
                (rCtx.AA - 7.95) / 14.36
            }

            rCtx.n1 = (rCtx.fs1 / df * d + 1).toInt()
            if (rCtx.n1 % 2 == 0) {
                rCtx.n1++
            }

            var alp = alpha(rCtx)
            iza = value(alp)

            //printf("iza = %g\n",iza);
            rCtx.ny = rCtx.fs1 / rCtx.sfrq
            rCtx.nx = rCtx.n1 / rCtx.ny + 1

            rCtx.fOrder = IntArray(rCtx.ny * rCtx.osf)
            i = 0
            while (i < rCtx.ny * rCtx.osf) {
                rCtx.fOrder[i] =
                    rCtx.fs1 / rCtx.sfrq - (i * (rCtx.fs1 / (rCtx.dfrq * rCtx.osf))) % (rCtx.fs1 / rCtx.sfrq)
                if (rCtx.fOrder[i] == rCtx.fs1 / rCtx.sfrq) {
                    rCtx.fOrder[i] = 0
                }
                i++
            }

            rCtx.fInc = IntArray(rCtx.ny * rCtx.osf)
            i = 0
            while (i < rCtx.ny * rCtx.osf) {
                rCtx.fInc[i] = if (rCtx.fOrder[i] < rCtx.fs1 / (rCtx.dfrq * rCtx.osf)) rCtx.nch else 0
                if (rCtx.fOrder[i] == rCtx.fs1 / rCtx.sfrq) {
                    rCtx.fOrder[i] = 0
                }
                i++
            }

            rCtx.stageB = Array(rCtx.ny) { DoubleArray(rCtx.nx) }

            i = -(rCtx.n1 / 2)
            while (i <= rCtx.n1 / 2) {
                rCtx.stageB[(i + rCtx.n1 / 2) % rCtx.ny][(i + rCtx.n1 / 2) / rCtx.ny] =
                    win(i.toDouble(), rCtx.n1, alp, iza) * hn_lpf(i, lpf, rCtx.fs1.toDouble()) * rCtx.fs1 / rCtx.sfrq
                i++
            }

            /* Make stage 2 filter */
            //var ipsize: Int
            //var wsize: Int

            d = if (rCtx.AA <= 21) {
                0.9222
            } else {
                (rCtx.AA - 7.95) / 14.36
            }

            rCtx.fs2 = rCtx.dfrq * rCtx.osf

            i = 1
            while (true) {
                rCtx.n2 = filter2len * i
                if (rCtx.n2 % 2 == 0) {
                    rCtx.n2--
                }
                df = (rCtx.fs2 * d) / (rCtx.n2 - 1)
                lpf = (rCtx.sfrq / 2).toDouble()
                if (df < rCtx.DF) {
                    break
                }
                i = i * 2
            }

            alp = alpha(rCtx)

            iza = value(alp)

            rCtx.nb = 1
            while (rCtx.nb < rCtx.n2) {
                rCtx.nb *= 2
            }
            rCtx.nb *= 2

            rCtx.stageA = DoubleArray(rCtx.nb)

            i = -(rCtx.n2 / 2)
            while (i <= rCtx.n2 / 2) {
                rCtx.stageA[i + rCtx.n2 / 2] =
                    win(i.toDouble(), rCtx.n2, alp, iza) * hn_lpf(i, lpf, rCtx.fs2.toDouble()) / rCtx.nb * 2
                i++
            }

            rCtx.fft!!.init(rCtx.nb)
            rCtx.fft!!.realDFT(rCtx.stageA)

            /* Apply filters */
            rCtx.nb2 = rCtx.nb / 2

            rCtx.buf1 = Array(rCtx.nch) { DoubleArray(rCtx.nb2 / rCtx.osf + 1) }
            rCtx.buf2 = Array(rCtx.nch) { DoubleArray(rCtx.nb) }

            rCtx.rawinbuf = ByteArray(rCtx.rnch * (rCtx.nb2 + rCtx.nx) * rCtx.bps)

            if (rCtx.twopass) {
                rCtx.rawoutbuf = ByteArray(rCtx.nch * (rCtx.nb2 / rCtx.osf + 1) * 8)
            } else {
                rCtx.rawoutbuf =
                    ByteArray(rCtx.dnch * (rCtx.nb2 / rCtx.osf + 1) * (if (rCtx.dstFloat) 4 else rCtx.dbps))
            }

            rCtx.inbuf = DoubleArray(rCtx.nch * (rCtx.nb2 + rCtx.nx))
            rCtx.outbuf = DoubleArray(rCtx.nch * (rCtx.nb2 / rCtx.osf + 1))

            rCtx.inbuflen = rCtx.n1 / 2 / (rCtx.fs1 / rCtx.sfrq) + 1
            rCtx.delay = (rCtx.n2.toDouble() / 2 / (rCtx.fs2 / rCtx.dfrq)).toInt()

            rCtx.inBuffer = ByteBuffer.wrap(rCtx.rawinbuf).order(rCtx.srcByteOrder)
            rCtx.outBuffer = ByteBuffer.wrap(rCtx.rawoutbuf).order(ByteOrder.LITTLE_ENDIAN)
        }

        private fun initDownSample(rCtx: ResampleContext) {
            val filter1len = rCtx.FFTFIRLEN // stage 1 filter length 

            // Make stage 1 filter 
            var lpf: Double
            var d: Double
            var df: Double
            var alp: Double
            var iza: Double
            //var ipsize: Int
            //var wsize: Int
            var i: Int

            rCtx.frqgcd = gcd(rCtx.sfrq, rCtx.dfrq)

            if (rCtx.dfrq / rCtx.frqgcd == 1) {
                rCtx.osf = 1
            } else if (rCtx.dfrq / rCtx.frqgcd % 2 == 0) {
                rCtx.osf = 2
            } else if (rCtx.dfrq / rCtx.frqgcd % 3 == 0) {
                rCtx.osf = 3
            } else {
                throw UnsupportedOperationException(
                    String.format(
                        """
                        Resampling from %dHz to %dHz is not supported.
                        %d/gcd(%d,%d)=%d must be divisible by 2 or 3.
                        """.trimIndent(),
                        rCtx.sfrq, rCtx.dfrq, rCtx.dfrq, rCtx.sfrq, rCtx.dfrq, rCtx.dfrq / rCtx.frqgcd
                    )
                )
            }

            rCtx.fs1 = rCtx.sfrq * rCtx.osf

            d = if (rCtx.AA <= 21) {
                0.9222
            } else {
                (rCtx.AA - 7.95) / 14.36
            }

            rCtx.n1 = filter1len
            i = 1
            while (true) {
                rCtx.n1 = filter1len * i
                if (rCtx.n1 % 2 == 0) {
                    rCtx.n1--
                }
                df = (rCtx.fs1 * d) / (rCtx.n1 - 1)
                lpf = (rCtx.dfrq - df) / 2
                if (df < rCtx.DF) {
                    break
                }
                i = i * 2
            }

            alp = alpha(rCtx)

            iza = value(alp)

            rCtx.nb = 1
            while (rCtx.nb < rCtx.n1) {
                rCtx.nb *= 2
            }
            rCtx.nb *= 2

            rCtx.stageA = DoubleArray(rCtx.nb)

            i = -(rCtx.n1 / 2)
            while (i <= rCtx.n1 / 2) {
                rCtx.stageA[i + rCtx.n1 / 2] = win(i.toDouble(), rCtx.n1, alp, iza) * hn_lpf(
                    i,
                    lpf,
                    rCtx.fs1.toDouble()
                ) * rCtx.fs1 / rCtx.sfrq / rCtx.nb * 2
                i++
            }

            rCtx.fft!!.init(rCtx.nb)
            rCtx.fft!!.realDFT(rCtx.stageA)

            // Make stage 2 filter 
            if (rCtx.osf == 1) {
                rCtx.fs2 = rCtx.sfrq / rCtx.frqgcd * rCtx.dfrq
                rCtx.n2 = 1
                rCtx.nx = 1
                rCtx.ny = rCtx.nx
                rCtx.fOrder = IntArray(rCtx.ny)
                rCtx.fInc = IntArray(rCtx.ny)
                rCtx.fInc[0] = rCtx.sfrq / rCtx.dfrq
                rCtx.stageB = Array(rCtx.ny) { DoubleArray(rCtx.nx) }
                rCtx.stageB[0][0] = 1.0
            } else {
                val guard = 2.0

                rCtx.fs2 = rCtx.sfrq / rCtx.frqgcd * rCtx.dfrq

                df = (rCtx.fs1 / 2 - rCtx.sfrq / 2) * 2 / guard
                lpf = rCtx.sfrq / 2 + (rCtx.fs1 / 2 - rCtx.sfrq / 2) / guard

                d = if (rCtx.AA <= 21) {
                    0.9222
                } else {
                    (rCtx.AA - 7.95) / 14.36
                }

                rCtx.n2 = (rCtx.fs2 / df * d + 1).toInt()
                if (rCtx.n2 % 2 == 0) {
                    rCtx.n2++
                }

                alp = alpha(rCtx)
                iza = value(alp)

                rCtx.ny = rCtx.fs2 / rCtx.fs1 // 0�Ǥʤ�����ץ뤬fs2�ǲ�����ץ뤪���ˤ��뤫��
                rCtx.nx = rCtx.n2 / rCtx.ny + 1

                rCtx.fOrder = IntArray(rCtx.ny)
                i = 0
                while (i < rCtx.ny) {
                    rCtx.fOrder[i] = rCtx.fs2 / rCtx.fs1 - (i * (rCtx.fs2 / rCtx.dfrq)) % (rCtx.fs2 / rCtx.fs1)
                    if (rCtx.fOrder[i] == rCtx.fs2 / rCtx.fs1) {
                        rCtx.fOrder[i] = 0
                    }
                    i++
                }

                rCtx.fInc = IntArray(rCtx.ny)
                i = 0
                while (i < rCtx.ny) {
                    rCtx.fInc[i] = (rCtx.fs2 / rCtx.dfrq - rCtx.fOrder[i]) / (rCtx.fs2 / rCtx.fs1) + 1
                    if (rCtx.fOrder[if (i + 1 == rCtx.ny) 0 else i + 1] == 0) {
                        rCtx.fInc[i]--
                    }
                    i++
                }

                rCtx.stageB = Array(rCtx.ny) { DoubleArray(rCtx.nx) }
                i = -(rCtx.n2 / 2)
                while (i <= rCtx.n2 / 2) {
                    rCtx.stageB[(i + rCtx.n2 / 2) % rCtx.ny][(i + rCtx.n2 / 2) / rCtx.ny] =
                        win(i.toDouble(), rCtx.n2, alp, iza) * hn_lpf(i, lpf, rCtx.fs2.toDouble()) * rCtx.fs2 / rCtx.fs1
                    i++
                }
            }

            rCtx.fs2fs1 = rCtx.fs2 / rCtx.fs1
            rCtx.fs2dfrq = rCtx.fs2 / rCtx.dfrq

            rCtx.nb2 = rCtx.nb / 2

            rCtx.buf1 = Array(rCtx.nch) { DoubleArray(rCtx.nb) }
            rCtx.buf2 = Array(rCtx.nch) { DoubleArray(rCtx.nx + 1 + rCtx.nb2) }

            rCtx.rawinbuf = ByteArray((rCtx.nb2 / rCtx.osf + rCtx.osf + 1) * rCtx.rnch * rCtx.bps)

            if (rCtx.twopass) {
                rCtx.rawoutbuf = ByteArray(((rCtx.nb2.toDouble() * rCtx.sfrq / rCtx.dfrq + 1) * 8 * rCtx.nch).toInt())
            } else {
                rCtx.rawoutbuf =
                    ByteArray(((rCtx.nb2.toDouble() * rCtx.sfrq / rCtx.dfrq + 1) * (if (rCtx.dstFloat) 4 else rCtx.dbps) * rCtx.dnch).toInt())
            }

            rCtx.inbuf = DoubleArray(rCtx.nch * (rCtx.nb2 / rCtx.osf + rCtx.osf + 1))
            rCtx.outbuf = DoubleArray((rCtx.nch * (rCtx.nb2.toDouble() * rCtx.sfrq / rCtx.dfrq + 1)).toInt())

            rCtx.inBuffer = ByteBuffer.wrap(rCtx.rawinbuf).order(rCtx.srcByteOrder)
            rCtx.outBuffer = ByteBuffer.wrap(rCtx.rawoutbuf).order(ByteOrder.LITTLE_ENDIAN)

            rCtx.delay =
                (rCtx.n1.toDouble() / 2 / (rCtx.fs1.toDouble() / rCtx.dfrq) + rCtx.n2.toDouble() / 2 / (rCtx.fs2.toDouble() / rCtx.dfrq)).toInt()
        }

        private fun do_shaping(rCtx: ResampleContext, s: Double, ch: Int): Int {
            var s1 = s

            if (rCtx.dither == 1) {
                s1 += rCtx.randbuf[rCtx.randptr++ and (RANDBUFLEN - 1)]

                if (s1 < rCtx.shaper_clipmin) {
                    val d = s1 / rCtx.shaper_clipmin
                    rCtx.peak = if (rCtx.peak < d) d else rCtx.peak
                    s1 = rCtx.shaper_clipmin.toDouble()
                }
                if (s1 > rCtx.shaper_clipmax) {
                    val d = s1 / rCtx.shaper_clipmax
                    rCtx.peak = if (rCtx.peak < d) d else rCtx.peak
                    s1 = rCtx.shaper_clipmax.toDouble()
                }

                return RINT(s1)
            }

            var h = 0.0
            var i = 0
            while (i < rCtx.shaper_len) {
                h += shapercoefs[rCtx.shaper_type][i] * rCtx.shapebuf[ch][i]
                i++
            }
            s1 += h
            val u = s1
            s1 += rCtx.randbuf[rCtx.randptr++ and (RANDBUFLEN - 1)]

            i = rCtx.shaper_len - 2
            while (i >= 0) {
                rCtx.shapebuf[ch][i + 1] = rCtx.shapebuf[ch][i]
                i--
            }

            if (s1 < rCtx.shaper_clipmin) {
                val d = s1 / rCtx.shaper_clipmin
                rCtx.peak = if (rCtx.peak < d) d else rCtx.peak
                s1 = rCtx.shaper_clipmin.toDouble()
                rCtx.shapebuf[ch][0] = s1 - u

                if (rCtx.shapebuf[ch][0] > 1) {
                    rCtx.shapebuf[ch][0] = 1.0
                }
                if (rCtx.shapebuf[ch][0] < -1) {
                    rCtx.shapebuf[ch][0] = -1.0
                }
            } else if (s1 > rCtx.shaper_clipmax) {
                val d = s1 / rCtx.shaper_clipmax
                rCtx.peak = if (rCtx.peak < d) d else rCtx.peak
                s1 = rCtx.shaper_clipmax.toDouble()
                rCtx.shapebuf[ch][0] = s1 - u

                if (rCtx.shapebuf[ch][0] > 1) {
                    rCtx.shapebuf[ch][0] = 1.0
                }
                if (rCtx.shapebuf[ch][0] < -1) {
                    rCtx.shapebuf[ch][0] = -1.0
                }
            } else {
                s1 = RINT(s1).toDouble()
                rCtx.shapebuf[ch][0] = s1 - u
            }

            return s1.toInt()
        }

        private fun alpha(rCtx: ResampleContext): Double {
            if (rCtx.AA <= 21) {
                return 0.0
            }
            if (rCtx.AA <= 50) {
                return 0.5842 * (rCtx.AA - 21).pow(0.4) + 0.07886 * (rCtx.AA - 21)
            }
            return 0.1102 * (rCtx.AA - 8.7)
        }

        private fun win(n: Double, len: Int, alp: Double, iza: Double): Double {
            return value(alp * sqrt(1 - 4 * n * n / ((len.toDouble() - 1) * (len.toDouble() - 1)))) / iza
        }

        private fun sinc(x: Double): Double {
            return if (x == 0.0) 1.0 else sin(x) / x
        }

        private fun hn_lpf(n: Int, lpf: Double, fs: Double): Double {
            val t = 1 / fs
            val omega = 2 * Math.PI * lpf
            return 2 * lpf * t * sinc(n * omega * t)
        }

        private fun gcd(x: Int, y: Int): Int {
            var x1 = x
            var y1 = y
            var t: Int

            while (y1 != 0) {
                t = x1 % y1
                x1 = y1
                y1 = t
            }
            return x1
        }

        /*private fun fillInBuf(rCtx: ResampleContext, samples: Array<FloatArray>, offset: Int, length: Int): Int {
            val ibOffset = rCtx.nch * rCtx.inbuflen
            val len = length * rCtx.nch
            var j = 0
            if (rCtx.nch == 1 && rCtx.rnch != rCtx.nch) {
                j = rCtx.mono
            }
            for (i in 0 until len) {
                rCtx.inbuf[ibOffset + i] = samples[i % rCtx.nch + j][offset + i / rCtx.nch].toDouble()
            }
            return length
        }*/

        /*private fun fillInBuf(rCtx: ResampleContext, samples: Array<IntArray>, offset: Int, length: Int): Int {
            val ibOffset = rCtx.nch * rCtx.inbuflen
            val len = length * rCtx.nch
            var j = 0
            if (rCtx.nch == 1 && rCtx.rnch != rCtx.nch) {
                j = rCtx.mono
            }

            when (rCtx.bps) {
                1 -> {
                    var i = 0
                    while (i < len) {
                        rCtx.inbuf[ibOffset + i] =
                            NORMALIZE_FACTOR_8 * ((samples[i % rCtx.nch + j][offset + i / rCtx.nch] and 0xff) - 128)
                        i++
                    }
                }

                2 -> {
                    var i = 0
                    while (i < len) {
                        rCtx.inbuf[ibOffset + i] =
                            NORMALIZE_FACTOR_16 * samples[i % rCtx.nch + j][offset + i / rCtx.nch]
                        i++
                    }
                }

                3 -> {
                    var i = 0
                    while (i < len) {
                        rCtx.inbuf[ibOffset + i] =
                            NORMALIZE_FACTOR_24 * samples[i % rCtx.nch + j][offset + i / rCtx.nch]
                        i++
                    }
                }

                4 -> {
                    var i = 0
                    while (i < len) {
                        rCtx.inbuf[ibOffset + i] =
                            NORMALIZE_FACTOR_32 * samples[i % rCtx.nch + j][offset + i / rCtx.nch]
                        i++
                    }
                }
            }
            return length
        }*/

        private fun fillInBuf(rCtx: ResampleContext, length: Int) {
            val offset = rCtx.nch * rCtx.inbuflen
            val len = length * rCtx.nch
            var j = 0
            var jCnt = rCtx.bps
            if (rCtx.nch == 1 && rCtx.rnch != rCtx.nch) {
                j = rCtx.mono * rCtx.bps
                jCnt = rCtx.rnch * rCtx.bps
            }
            if (rCtx.srcFloat) {
                var i = 0
                while (i < len) {
                    rCtx.inbuf[offset + i] = rCtx.inBuffer!!.getDouble(j)
                    i++
                    j += jCnt
                }
            } else {
                when (rCtx.bps) {
                    1 -> {
                        var i = 0
                        while (i < len) {
                            rCtx.inbuf[offset + i] =
                                NORMALIZE_FACTOR_8 * ((rCtx.inBuffer!![j].toShort().toInt() and 0xff) - 128).toDouble()
                            i++
                            j += jCnt
                        }
                    }

                    2 -> {
                        var i = 0
                        while (i < len) {
                            rCtx.inbuf[offset + i] = NORMALIZE_FACTOR_16 * rCtx.inBuffer!!.getShort(j).toDouble()
                            i++
                            j += jCnt
                        }
                    }

                    3 -> if (rCtx.srcByteOrder == ByteOrder.LITTLE_ENDIAN) {
                        var i = 0
                        while (i < len) {
                            rCtx.inbuf[offset + i] = (NORMALIZE_FACTOR_24
                                    * ((rCtx.inBuffer!!.getShort(j)
                                .toInt() and 0xffff) or ((rCtx.inBuffer!![j + 2].toInt() shl 24) shr 8)).toDouble())
                            i++
                            j += jCnt
                        }
                    } else {
                        var i = 0
                        while (i < len) {
                            rCtx.inbuf[offset + i] = (NORMALIZE_FACTOR_24
                                    * (((rCtx.inBuffer!![j].toInt() shl 24) shr 8) or (rCtx.inBuffer!!.getShort(j + 1)
                                .toInt() and 0xffff)).toDouble())
                            i++
                            j += jCnt
                        }
                    }

                    4 -> {
                        var i = 0
                        while (i < len) {
                            rCtx.inbuf[offset + i] = NORMALIZE_FACTOR_32 * rCtx.inBuffer!!.getInt(j).toDouble()
                            i++
                            j += jCnt
                        }
                    }
                }
            }
        }

        private fun fillOutBuf(rCtx: ResampleContext, dbps: Int, gain: Double, nsmplwrt: Int) {
            var i: Int
            var j: Int
            var s: Int
            val gain2: Double
            var d: Double
            var d2: Double
            if (rCtx.twopass) {
                i = 0
                while (i < nsmplwrt * rCtx.nch) {
                    d = rCtx.outbuf[i]
                    rCtx.outBuffer!!.putDouble(d)
                    d = abs(d)
                    rCtx.peak = if (rCtx.peak < d) d else rCtx.peak
                    i++
                }
            } else if (rCtx.dstFloat) {
                if (rCtx.dither > 0) {
                    gain2 = gain * (0x7fffff).toDouble()
                    i = 0
                    while (i < nsmplwrt) {
                        j = 0
                        while (j < rCtx.dnch) {
                            s = do_shaping(rCtx, rCtx.outbuf[i * rCtx.nch + (j % rCtx.nch)] * gain2, (j % rCtx.nch))
                            rCtx.outBuffer!!.putFloat(((1.0 / 0x7fffff) * s).toFloat())
                            j++
                        }
                        i++
                    }
                } else {
                    i = 0
                    while (i < nsmplwrt) {
                        j = 0
                        while (j < rCtx.dnch) {
                            d = rCtx.outbuf[i * rCtx.nch + (j % rCtx.nch)] * gain
                            d2 = abs(d)
                            if (d2 > 1.0) {
                                d /= d2
                                rCtx.peak = if (rCtx.peak < d2) d2 else rCtx.peak
                            }
                            rCtx.outBuffer!!.putFloat(d.toFloat())
                            j++
                        }
                        i++
                    }
                }
            } else {
                when (dbps) {
                    1 -> {
                        gain2 = gain * (0x7f).toDouble()
                        if (rCtx.dither > 0) {
                            i = 0
                            while (i < nsmplwrt) {
                                j = 0
                                while (j < rCtx.dnch) {
                                    s = do_shaping(
                                        rCtx,
                                        rCtx.outbuf[i * rCtx.nch + (j % rCtx.nch)] * gain2,
                                        (j % rCtx.nch)
                                    )
                                    rCtx.outBuffer!!.put((s + 0x80).toByte()) //	((unsigned char *)rawoutbuf)[i] = s + 0x80;
                                    j++
                                }
                                i++
                            }
                        } else {
                            i = 0
                            while (i < nsmplwrt) {
                                j = 0
                                while (j < rCtx.dnch) {
                                    s = RINT(rCtx.outbuf[i * rCtx.nch + (j % rCtx.nch)] * gain2)

                                    if (s < -0x80) {
                                        d = s.toDouble() / -0x80
                                        rCtx.peak = if (rCtx.peak < d) d else rCtx.peak
                                        s = -0x80
                                    }
                                    if (0x7f < s) {
                                        d = s.toDouble() / 0x7f
                                        rCtx.peak = if (rCtx.peak < d) d else rCtx.peak
                                        s = 0x7f
                                    }
                                    rCtx.outBuffer!!.put((s + 0x80).toByte()) //	((unsigned char *)rawoutbuf)[i] = s + 0x80;
                                    j++
                                }
                                i++
                            }
                        }
                    }

                    2 -> {
                        gain2 = gain * (0x7fff).toDouble()
                        if (rCtx.dither > 0) {
                            i = 0
                            while (i < nsmplwrt) {
                                j = 0
                                while (j < rCtx.dnch) {
                                    s = do_shaping(
                                        rCtx,
                                        rCtx.outbuf[i * rCtx.nch + (j % rCtx.nch)] * gain2,
                                        (j % rCtx.nch)
                                    )
                                    rCtx.outBuffer!!.putShort(s.toShort())
                                    j++
                                }
                                i++
                            }
                        } else {
                            i = 0
                            while (i < nsmplwrt) {
                                j = 0
                                while (j < rCtx.dnch) {
                                    s = RINT(rCtx.outbuf[i * rCtx.nch + (j % rCtx.nch)] * gain2)

                                    if (s < -0x8000) {
                                        d = s.toDouble() / -0x8000
                                        rCtx.peak = if (rCtx.peak < d) d else rCtx.peak
                                        s = -0x8000
                                    }
                                    if (0x7fff < s) {
                                        d = s.toDouble() / 0x7fff
                                        rCtx.peak = if (rCtx.peak < d) d else rCtx.peak
                                        s = 0x7fff
                                    }
                                    rCtx.outBuffer!!.putShort(s.toShort())
                                    j++
                                }
                                i++
                            }
                        }
                    }

                    3 -> {
                        gain2 = gain * (0x7fffff).toDouble()
                        if (rCtx.dither > 0) {
                            i = 0
                            while (i < nsmplwrt) {
                                j = 0
                                while (j < rCtx.dnch) {
                                    s = do_shaping(
                                        rCtx,
                                        rCtx.outbuf[i * rCtx.nch + (j % rCtx.nch)] * gain2,
                                        (j % rCtx.nch)
                                    )
                                    rCtx.outBuffer!!.putShort(s.toShort())
                                    s = s shr 16
                                    rCtx.outBuffer!!.put(s.toByte())
                                    j++
                                }
                                i++
                            }
                        } else {
                            i = 0
                            while (i < nsmplwrt) {
                                j = 0
                                while (j < rCtx.dnch) {
                                    s = RINT(rCtx.outbuf[i * rCtx.nch + (j % rCtx.nch)] * gain2)

                                    if (s < -0x800000) {
                                        d = s.toDouble() / -0x800000
                                        rCtx.peak = if (rCtx.peak < d) d else rCtx.peak
                                        s = -0x800000
                                    }
                                    if (0x7fffff < s) {
                                        d = s.toDouble() / 0x7fffff
                                        rCtx.peak = if (rCtx.peak < d) d else rCtx.peak
                                        s = 0x7fffff
                                    }
                                    rCtx.outBuffer!!.putShort(s.toShort())
                                    s = s shr 16
                                    rCtx.outBuffer!!.put(s.toByte())
                                    j++
                                }
                                i++
                            }
                        }
                    }
                }
            }
        }

        private fun writeOutBytes(rCtx: ResampleContext, nsmplwrt: Int, dbps: Int, offset: Int, isLast: Boolean): Int {
            var writeLen = 0
            var writeOffset = 0
            val nch = if (rCtx.twopass) rCtx.nch else rCtx.dnch

            if (!rCtx.init) {
                if (isLast) {
                    if (rCtx.sumread.toDouble() * rCtx.dfrq / rCtx.sfrq + 2 > rCtx.sumwrite + nsmplwrt) {
                        writeLen = dbps * nch * nsmplwrt
                    } else {
                        writeLen =
                            dbps * nch * (floor(rCtx.sumread.toDouble() * rCtx.dfrq / rCtx.sfrq) + 2 - rCtx.sumwrite).toInt()
                        reset(rCtx)
                    }
                } else {
                    writeLen = dbps * nch * nsmplwrt
                }
            } else {
                if (nsmplwrt < rCtx.delay) {
                    rCtx.delay -= nsmplwrt
                } else {
                    writeOffset = dbps * nch * rCtx.delay
                    if (isLast) {
                        if (rCtx.sumread.toDouble() * rCtx.dfrq / rCtx.sfrq + 2 > rCtx.sumwrite + nsmplwrt - rCtx.delay) {
                            writeLen = dbps * nch * (nsmplwrt - rCtx.delay)
                        } else {
                            writeLen =
                                (dbps * nch * (floor(rCtx.sumread.toDouble() * rCtx.dfrq / rCtx.sfrq) + 2 - rCtx.sumwrite - rCtx.delay)).toInt()
                            reset(rCtx)
                        }
                    } else {
                        writeLen = dbps * nch * (nsmplwrt - rCtx.delay)
                        rCtx.init = false
                    }
                }
            }

            if (writeLen > 0) {
                if (rCtx.outBytes == null) {
                    rCtx.outBytes = ByteArray(rCtx.outBuffer!!.limit())
                }
                if (rCtx.outBytes!!.size - offset < rCtx.outBuffer!!.limit()) {
                    val tmpBytes = ByteArray(offset + rCtx.outBuffer!!.limit())
                    System.arraycopy(rCtx.outBytes!!, 0, tmpBytes, 0, offset)
                    rCtx.outBytes = tmpBytes
                }
                rCtx.outBuffer!!.position(writeOffset)
                rCtx.outBuffer!![rCtx.outBytes, offset, writeLen]
            }

            return writeLen
        }

        /*private fun writeOutStream(
            rCtx: ResampleContext,
            fpo: OutputStream,
            nsmplwrt: Int,
            dbps: Int,
            isLast: Boolean
        ): Boolean {
            val nch = if (rCtx.twopass) rCtx.nch else rCtx.dnch

            if (!rCtx.init) {
                if (isLast) {
                    if (rCtx.sumread.toDouble() * rCtx.dfrq / rCtx.sfrq + 2 > rCtx.sumwrite + nsmplwrt) {
                        fpo.write(rCtx.rawoutbuf, 0, dbps * nch * nsmplwrt)
                        rCtx.sumwrite += nsmplwrt.toLong()
                    } else {
                        val limitData =
                            (dbps * nch * (floor(rCtx.sumread.toDouble() * rCtx.dfrq / rCtx.sfrq) + 2 - rCtx.sumwrite)).toInt()
                        if (limitData > 0) {
                            fpo.write(rCtx.rawoutbuf, 0, limitData)
                        }
                        reset(rCtx)
                        return true
                    }
                } else {
                    fpo.write(rCtx.rawoutbuf, 0, dbps * nch * nsmplwrt)
                    rCtx.sumwrite += nsmplwrt.toLong()
                }
            } else {
                if (nsmplwrt < rCtx.delay) {
                    rCtx.delay -= nsmplwrt
                } else {
                    if (isLast) {
                        if (rCtx.sumread.toDouble() * rCtx.dfrq / rCtx.sfrq + 2 > rCtx.sumwrite + nsmplwrt - rCtx.delay) {
                            fpo.write(rCtx.rawoutbuf, dbps * nch * rCtx.delay, dbps * nch * (nsmplwrt - rCtx.delay))
                            rCtx.sumwrite += (nsmplwrt - rCtx.delay).toLong()
                        } else {
                            fpo.write(
                                rCtx.rawoutbuf,
                                dbps * nch * rCtx.delay,
                                (dbps * nch * (floor(rCtx.sumread.toDouble() * rCtx.dfrq / rCtx.sfrq) + 2 - rCtx.sumwrite - rCtx.delay)).toInt()
                            )
                            reset(rCtx)
                            return true
                        }
                    } else {
                        fpo.write(rCtx.rawoutbuf, dbps * nch * rCtx.delay, dbps * nch * (nsmplwrt - rCtx.delay))
                        rCtx.sumwrite += (nsmplwrt - rCtx.delay).toLong()
                        rCtx.init = false
                    }
                }
            }
            return false
        }*/

        private fun upSample(rCtx: ResampleContext, nsmplwrt1: Int): Int {
            var p: Int
            var i: Int
            var j: Int
            var s1o: Int
            var no: Int
            var ip2: Int
            var nsmplwrt2 = 0
            val s1p_backup = rCtx.sp
            val ip_backup = rCtx.ip
            val osc_backup = rCtx.osc
            var re: Double
            var im: Double
            var d: Double
            var tmp: Double

            // apply stage 1 filter
            var ch = 0
            while (ch < rCtx.nch) {
                no = rCtx.ny * rCtx.osf

                rCtx.sp = s1p_backup
                rCtx.ip = ip_backup + ch

                when (rCtx.nx) {
                    7 -> {
                        p = 0
                        while (p < nsmplwrt1) {
                            s1o = rCtx.fOrder[rCtx.sp]

                            rCtx.buf2[ch][p] = (rCtx.stageB[s1o][0] * rCtx.inbuf[rCtx.ip] + rCtx.stageB[s1o][1] * rCtx.inbuf[rCtx.ip + rCtx.nch] + rCtx.stageB[s1o][2] * rCtx.inbuf[rCtx.ip + 2 * rCtx.nch] + rCtx.stageB[s1o][3] * rCtx.inbuf[rCtx.ip + 3 * rCtx.nch] + rCtx.stageB[s1o][4] * rCtx.inbuf[rCtx.ip + 4 * rCtx.nch] + rCtx.stageB[s1o][5] * rCtx.inbuf[rCtx.ip + 5 * rCtx.nch] + rCtx.stageB[s1o][6] * rCtx.inbuf[rCtx.ip + 6 * rCtx.nch])

                            rCtx.ip += rCtx.fInc[rCtx.sp]

                            rCtx.sp++
                            if (rCtx.sp == no) {
                                rCtx.sp = 0
                            }
                            p++
                        }
                    }

                    9 -> {
                        p = 0
                        while (p < nsmplwrt1) {
                            s1o = rCtx.fOrder[rCtx.sp]

                            rCtx.buf2[ch][p] = (rCtx.stageB[s1o][0] * rCtx.inbuf[rCtx.ip] + rCtx.stageB[s1o][1] * rCtx.inbuf[rCtx.ip + rCtx.nch] + rCtx.stageB[s1o][2] * rCtx.inbuf[rCtx.ip + 2 * rCtx.nch] + rCtx.stageB[s1o][3] * rCtx.inbuf[rCtx.ip + 3 * rCtx.nch] + rCtx.stageB[s1o][4] * rCtx.inbuf[rCtx.ip + 4 * rCtx.nch] + rCtx.stageB[s1o][5] * rCtx.inbuf[rCtx.ip + 5 * rCtx.nch] + rCtx.stageB[s1o][6] * rCtx.inbuf[rCtx.ip + 6 * rCtx.nch] + rCtx.stageB[s1o][7] * rCtx.inbuf[rCtx.ip + 7 * rCtx.nch] + rCtx.stageB[s1o][8] * rCtx.inbuf[rCtx.ip + 8 * rCtx.nch])

                            rCtx.ip += rCtx.fInc[rCtx.sp]

                            rCtx.sp++
                            if (rCtx.sp == no) {
                                rCtx.sp = 0
                            }
                            p++
                        }
                    }

                    else -> {
                        p = 0
                        while (p < nsmplwrt1) {
                            tmp = 0.0
                            ip2 = rCtx.ip

                            s1o = rCtx.fOrder[rCtx.sp]

                            i = 0
                            while (i < rCtx.nx) {
                                tmp += rCtx.stageB[s1o][i] * rCtx.inbuf[ip2]
                                ip2 += rCtx.nch
                                i++
                            }
                            rCtx.buf2[ch][p] = tmp

                            rCtx.ip += rCtx.fInc[rCtx.sp]

                            rCtx.sp++
                            if (rCtx.sp == no) {
                                rCtx.sp = 0
                            }
                            p++
                        }
                    }
                }

                rCtx.osc = osc_backup

                // apply stage 2 filter
                rCtx.buf2[ch].fill(0.0, nsmplwrt1, rCtx.nb)

                rCtx.fft!!.realDFT(rCtx.buf2[ch])

                rCtx.buf2[ch][0] = rCtx.stageA[0] * rCtx.buf2[ch][0]
                rCtx.buf2[ch][1] = rCtx.stageA[1] * rCtx.buf2[ch][1]

                i = 1
                while (i < rCtx.nb / 2) {
                    re = rCtx.stageA[i * 2] * rCtx.buf2[ch][i * 2] - rCtx.stageA[i * 2 + 1] * rCtx.buf2[ch][i * 2 + 1]
                    im = rCtx.stageA[i * 2 + 1] * rCtx.buf2[ch][i * 2] + rCtx.stageA[i * 2] * rCtx.buf2[ch][i * 2 + 1]

                    //System.out.println(String.format("%d : %g %g %g %g %g %g\n",i,rCtx.stageA[i*2],rCtx.stageA[i*2+1],rCtx.buf2[ch][i*2],rCtx.buf2[ch][i*2+1],re,im));
                    rCtx.buf2[ch][i * 2] = re
                    rCtx.buf2[ch][i * 2 + 1] = im
                    i++
                }

                rCtx.fft!!.realInverseDFT(rCtx.buf2[ch])

                i = rCtx.osc
                j = 0
                while (i < rCtx.nb2) {
                    d = (rCtx.buf1[ch][j] + rCtx.buf2[ch][i])
                    rCtx.outbuf[ch + j * rCtx.nch] = d
                    i += rCtx.osf
                    j++
                }

                nsmplwrt2 = j

                rCtx.osc = i - rCtx.nb2

                j = 0
                while (i < rCtx.nb) {
                    rCtx.buf1[ch][j] = rCtx.buf2[ch][i]
                    i += rCtx.osf
                    j++
                }
                ch++
            }

            return nsmplwrt2
        }

        private fun downSample(rCtx: ResampleContext): Int {
            val rps_backup = rCtx.rps
            val s2p_backup = rCtx.sp
            var i: Int
            var j: Int
            var k: Int
            var t1: Int
            var bp: Int
            var nsmplwrt2 = 0
            var re: Double
            var im: Double
            var tmp: Double
            var bp2: Int
            var s2o: Int

            var ch = 0
            while (ch < rCtx.nch) {
                rCtx.rps = rps_backup

                rCtx.buf1[ch].fill(0.0, 0, rCtx.rps)

                i = rCtx.rps
                j = 0
                while (i < rCtx.nb2) {
                    //				assert(j < ((rCtx.nb2-rCtx.rps-1)/rCtx.osf+1));
                    rCtx.buf1[ch][i] = rCtx.inbuf[j * rCtx.nch + ch]
                    k = i + 1
                    while (k < i + rCtx.osf) {
                        rCtx.buf1[ch][k] = 0.0
                        k++
                    }
                    i += rCtx.osf
                    j++
                }

                //			assert(j == ((rCtx.nb2-rCtx.rps-1)/rCtx.osf+1));
                rCtx.buf1[ch].fill(0.0, rCtx.nb2, rCtx.nb)

                rCtx.rps = i - rCtx.nb2
                rCtx.fft!!.realDFT(rCtx.buf1[ch])
                rCtx.buf1[ch][0] = rCtx.stageA[0] * rCtx.buf1[ch][0]
                rCtx.buf1[ch][1] = rCtx.stageA[1] * rCtx.buf1[ch][1]
                i = 1
                while (i < rCtx.nb2) {
                    re = rCtx.stageA[i * 2] * rCtx.buf1[ch][i * 2] - rCtx.stageA[i * 2 + 1] * rCtx.buf1[ch][i * 2 + 1]
                    im = rCtx.stageA[i * 2 + 1] * rCtx.buf1[ch][i * 2] + rCtx.stageA[i * 2] * rCtx.buf1[ch][i * 2 + 1]

                    rCtx.buf1[ch][i * 2] = re
                    rCtx.buf1[ch][i * 2 + 1] = im
                    i++
                }

                rCtx.fft!!.realInverseDFT(rCtx.buf1[ch])
                i = 0
                while (i < rCtx.nb2) {
                    rCtx.buf2[ch][rCtx.nx + 1 + i] += rCtx.buf1[ch][i]
                    i++
                }

                t1 = rCtx.rp / (rCtx.fs2 / rCtx.fs1)
                if (rCtx.rp % (rCtx.fs2 / rCtx.fs1) != 0) {
                    t1++
                }

                bp = t1 // bp = &(buf2[ch][t1]);
                rCtx.sp = s2p_backup

                j = 0
                while (bp < rCtx.nb2 + 1) {
                    tmp = 0.0
                    bp2 = bp
                    s2o = rCtx.fOrder[rCtx.sp]
                    bp += rCtx.fInc[rCtx.sp]
                    rCtx.sp++

                    if (rCtx.sp == rCtx.ny) {
                        rCtx.sp = 0
                    }

                    //				assert(bp2*(rCtx.fs2/rCtx.fs1)-(rCtx.rp+j*(rCtx.fs2/rCtx.dfrq)) == s2o);
                    i = 0
                    while (i < rCtx.nx) {
                        tmp += rCtx.stageB[s2o][i] * rCtx.buf2[ch][bp2++]
                        i++
                    }

                    rCtx.outbuf[j * rCtx.nch + ch] = tmp
                    j++
                }

                nsmplwrt2 = j
                ch++
            }
            return nsmplwrt2
        }

        /*private fun upsample(
            rCtx: ResampleContext,
            samples: Array<FloatArray>,
            length: Int,
            gain: Double,
            isLast: Boolean
        ): Int {
            var nsmplwrt1 = rCtx.nb2
            var nsmplwrt2: Int
            var writeLen: Int
            val dbps = if (rCtx.twopass) 8 else if (rCtx.dstFloat) 4 else rCtx.dbps
            val tobereadbase = floor(rCtx.nb2.toDouble() * rCtx.sfrq / (rCtx.dfrq * rCtx.osf)).toInt() + 1 + rCtx.nx
            var toberead = tobereadbase - rCtx.inbuflen

            if (length < toberead && !isLast) {
                rCtx.inbuflen += fillInBuf(rCtx, samples, 0, length)
                rCtx.sumread += length.toLong()
                return 0
            }

            if (length == 0 && rCtx.inbuflen > 0 && isLast) {
                rCtx.inbuf.fill(0.0, rCtx.inbuflen * rCtx.nch, tobereadbase * rCtx.nch)
                nsmplwrt1 = rCtx.nb2
                rCtx.ip = ((rCtx.sfrq * (rCtx.rp - 1) + rCtx.fs1) / rCtx.fs1) * rCtx.nch
                nsmplwrt2 = upSample(rCtx, nsmplwrt1)
                rCtx.rp += nsmplwrt1 * rCtx.sfrqfrqgcd / rCtx.osf
                rCtx.outBuffer!!.clear()
                fillOutBuf(rCtx, dbps, gain, nsmplwrt2)
                rCtx.outBuffer!!.flip()
                return writeOutBytes(rCtx, nsmplwrt2, dbps, 0, true)
            }

            var outBytesWritten = 0
            var lenUsed = 0
            while (lenUsed < length) {
                toberead = tobereadbase - rCtx.inbuflen


                if (length - lenUsed < toberead) {
                    if (!isLast) {
                        rCtx.inbuflen += fillInBuf(rCtx, samples, lenUsed, length - lenUsed)
                        rCtx.sumread += (length - lenUsed).toLong()
                        return outBytesWritten
                    }
                    rCtx.inbuf.fill(0.0, rCtx.inbuflen * rCtx.nch, tobereadbase * rCtx.nch)
                    toberead = length - lenUsed
                }
                rCtx.inbuflen += fillInBuf(rCtx, samples, lenUsed, toberead)
                lenUsed += toberead
                rCtx.sumread += toberead.toLong()


                rCtx.ip = ((rCtx.sfrq * (rCtx.rp - 1) + rCtx.fs1) / rCtx.fs1) * rCtx.nch

                nsmplwrt2 = upSample(rCtx, nsmplwrt1)

                rCtx.rp += nsmplwrt1 * rCtx.sfrqfrqgcd / rCtx.osf

                rCtx.outBuffer!!.clear()
                fillOutBuf(rCtx, dbps, gain, nsmplwrt2)
                rCtx.outBuffer!!.flip()

                writeLen = writeOutBytes(rCtx, nsmplwrt2, dbps, outBytesWritten, isLast)
                if (writeLen < 0) {
                    break
                }
                outBytesWritten += writeLen

                rCtx.sumwrite += (writeLen / rCtx.wbpf).toLong()

                rCtx.ds = (rCtx.rp - 1) / rCtx.fs1sfrq

                if (rCtx.inbuflen > rCtx.ds) System.arraycopy(
                    rCtx.inbuf,
                    rCtx.nch * rCtx.ds,
                    rCtx.inbuf,
                    0,
                    rCtx.nch * (rCtx.inbuflen - rCtx.ds)
                )

                rCtx.inbuflen -= rCtx.ds
                rCtx.rp -= rCtx.ds * rCtx.fs1sfrq
            }
            return outBytesWritten
        }*/

        /*private fun downsample(
            rCtx: ResampleContext,
            samples: Array<FloatArray>,
            length: Int,
            gain: Double,
            isLast: Boolean
        ): Int {
            var nsmplwrt: Int
            var writeLen: Int
            val dbps = if (rCtx.twopass) 8 else if (rCtx.dstFloat) 4 else rCtx.dbps
            var toberead = ((rCtx.nb2 - rCtx.rps - 1) / rCtx.osf + 1)

            if (rCtx.inbuflen + length < toberead && !isLast) {
                rCtx.inbuflen += fillInBuf(rCtx, samples, 0, length)
                rCtx.sumread += length.toLong()
                return 0
            }

            if (length == 0 && rCtx.inbuflen > 0 && isLast) {
                rCtx.inbuf.fill(0.0, rCtx.inbuflen * rCtx.nch, toberead * rCtx.nch)
                nsmplwrt = downSample(rCtx)
                rCtx.inbuflen = 0
                rCtx.rp += nsmplwrt * (rCtx.fs2 / rCtx.dfrq)
                rCtx.outBuffer!!.clear()
                fillOutBuf(rCtx, dbps, gain, nsmplwrt)
                rCtx.outBuffer!!.flip()
                return writeOutBytes(rCtx, nsmplwrt, dbps, 0, true)
            }

            var outBytesWritten = 0
            var lenUsed = 0

            while (lenUsed < length) {
                toberead = ((rCtx.nb2 - rCtx.rps - 1) / rCtx.osf + 1) - rCtx.inbuflen

                if (length - lenUsed < toberead) {
                    if (!isLast) {
                        rCtx.inbuflen += fillInBuf(rCtx, samples, lenUsed, length - lenUsed)
                        rCtx.sumread += (length - lenUsed).toLong()
                        return outBytesWritten
                    }
                    rCtx.inbuf.fill(0.0, rCtx.inbuflen * rCtx.nch, (toberead + rCtx.inbuflen) * rCtx.nch)
                    toberead = length - lenUsed
                }
                rCtx.inbuflen += fillInBuf(rCtx, samples, lenUsed, toberead)
                lenUsed += toberead

                rCtx.sumread += toberead.toLong()

                nsmplwrt = downSample(rCtx)
                rCtx.inbuflen = 0
                rCtx.rp += nsmplwrt * rCtx.fs2dfrq

                rCtx.outBuffer!!.clear()
                fillOutBuf(rCtx, dbps, gain, nsmplwrt)
                rCtx.outBuffer!!.flip()

                writeLen = writeOutBytes(rCtx, nsmplwrt, dbps, outBytesWritten, isLast)
                if (writeLen < 0) {
                    break
                }
                outBytesWritten += writeLen

                rCtx.sumwrite += (writeLen / rCtx.wbpf).toLong()

                rCtx.ds = (rCtx.rp - 1) / rCtx.fs2fs1

                if (rCtx.ds > rCtx.nb2) {
                    rCtx.ds = rCtx.nb2
                }
                var ch = 0
                while (ch < rCtx.nch) {
                    System.arraycopy(rCtx.buf2[ch], rCtx.ds, rCtx.buf2[ch], 0, rCtx.nx + 1 + rCtx.nb2 - rCtx.ds)
                    ch++
                }

                rCtx.rp -= rCtx.ds * rCtx.fs2fs1

                ch = 0
                while (ch < rCtx.nch) {
                    System.arraycopy(rCtx.buf1[ch], rCtx.nb2, rCtx.buf2[ch], rCtx.nx + 1, rCtx.nb2)
                    ch++
                }
            }
            return outBytesWritten
        }*/

        /*private fun no_src(rCtx: ResampleContext, samples: Array<FloatArray>, length: Int, gain: Double): Int {
            var i: Int
            var ch: Int
            var f: Double
            var p: Double
            var len: Int

            var outBytesWritten = 0
            var lenUsed = 0

            var j = 0
            if (rCtx.nch == 1 && rCtx.rnch != rCtx.nch) {
                j = rCtx.mono
            }

            while (lenUsed < length) {
                len = length - lenUsed
                rCtx.outBuffer!!.clear()
                if (len > rCtx.outBuffer!!.limit() / rCtx.nch) {
                    len = rCtx.outBuffer!!.limit() / rCtx.nch
                }
                lenUsed += len

                if (rCtx.twopass) {
                    i = 0
                    while (i < len) {
                        ch = 0
                        while (ch < rCtx.nch) {
                            f = samples[ch % rCtx.nch + j][i] * gain
                            p = if (f > 0) f else -f
                            rCtx.peak = if (rCtx.peak < p) p else rCtx.peak
                            rCtx.outBuffer!!.putDouble(f)
                            ch++
                        }
                        i++
                    }
                } else {
                    i = 0
                    while (i < len) {
                        ch = 0
                        while (ch < rCtx.dnch) {
                            f = samples[ch % rCtx.nch + j][i] * gain
                            writeToOutBuffer(rCtx, f, (ch % rCtx.nch))
                            ch++
                        }
                        i++
                    }
                }
                rCtx.outBuffer!!.flip()
                if (rCtx.outBytes!!.size - outBytesWritten < rCtx.outBuffer!!.limit()) {
                    val tmpBytes = ByteArray(outBytesWritten + rCtx.outBuffer!!.limit())
                    System.arraycopy(rCtx.outBytes!!, 0, tmpBytes, 0, outBytesWritten)
                    rCtx.outBytes = tmpBytes
                }
                rCtx.outBuffer!![rCtx.outBytes, outBytesWritten, rCtx.outBuffer!!.limit()]
                outBytesWritten += rCtx.outBuffer!!.limit()
            }
            return outBytesWritten
        }*/

        /*private fun upsample(
            rCtx: ResampleContext,
            samples: Array<IntArray>,
            length: Int,
            gain: Double,
            isLast: Boolean
        ): Int {
            var nsmplwrt1: Int
            var nsmplwrt2: Int
            var writeLen: Int
            val dbps = if (rCtx.twopass) 8 else if (rCtx.dstFloat) 4 else rCtx.dbps
            val tobereadbase = floor(rCtx.nb2.toDouble() * rCtx.sfrq / (rCtx.dfrq * rCtx.osf)).toInt() + 1 + rCtx.nx
            var toberead = tobereadbase - rCtx.inbuflen

            if (length < toberead && !isLast) {
                rCtx.inbuflen += fillInBuf(rCtx, samples, 0, length)
                rCtx.sumread += length.toLong()
                return 0
            }

            if (length == 0 && rCtx.inbuflen > 0 && isLast) {
                rCtx.inbuf.fill(0.0, rCtx.inbuflen * rCtx.nch, tobereadbase * rCtx.nch)
                nsmplwrt1 = rCtx.nb2
                rCtx.ip = ((rCtx.sfrq * (rCtx.rp - 1) + rCtx.fs1) / rCtx.fs1) * rCtx.nch
                nsmplwrt2 = upSample(rCtx, nsmplwrt1)
                rCtx.rp += nsmplwrt1 * rCtx.sfrqfrqgcd / rCtx.osf
                rCtx.outBuffer!!.clear()
                fillOutBuf(rCtx, dbps, gain, nsmplwrt2)
                rCtx.outBuffer!!.flip()
                return writeOutBytes(rCtx, nsmplwrt2, dbps, 0, true)
            }

            var outBytesWritten = 0
            var lenUsed = 0

            while (lenUsed < length) {
                toberead = tobereadbase - rCtx.inbuflen

                if (length - lenUsed < toberead) {
                    if (!isLast) {
                        rCtx.inbuflen += fillInBuf(rCtx, samples, lenUsed, length - lenUsed)
                        rCtx.sumread += (length - lenUsed).toLong()
                        return outBytesWritten
                    }
                    rCtx.inbuf.fill(0.0, rCtx.inbuflen * rCtx.nch, tobereadbase * rCtx.nch)
                    toberead = length - lenUsed
                }
                rCtx.inbuflen += fillInBuf(rCtx, samples, lenUsed, toberead)
                lenUsed += toberead
                rCtx.sumread += toberead.toLong()

                nsmplwrt1 = rCtx.nb2

                rCtx.ip = ((rCtx.sfrq * (rCtx.rp - 1) + rCtx.fs1) / rCtx.fs1) * rCtx.nch

                nsmplwrt2 = upSample(rCtx, nsmplwrt1)

                rCtx.rp += nsmplwrt1 * rCtx.sfrqfrqgcd / rCtx.osf

                rCtx.outBuffer!!.clear()
                fillOutBuf(rCtx, dbps, gain, nsmplwrt2)
                rCtx.outBuffer!!.flip()

                writeLen = writeOutBytes(rCtx, nsmplwrt2, dbps, outBytesWritten, isLast)
                if (writeLen < 0) {
                    break
                }
                outBytesWritten += writeLen

                rCtx.sumwrite += (writeLen / rCtx.wbpf).toLong()

                rCtx.ds = (rCtx.rp - 1) / rCtx.fs1sfrq

                if (rCtx.inbuflen > rCtx.ds) System.arraycopy(
                    rCtx.inbuf,
                    rCtx.nch * rCtx.ds,
                    rCtx.inbuf,
                    0,
                    rCtx.nch * (rCtx.inbuflen - rCtx.ds)
                )

                rCtx.inbuflen -= rCtx.ds
                rCtx.rp -= rCtx.ds * rCtx.fs1sfrq
            }
            return outBytesWritten
        }*/

        /*private fun downsample(
            rCtx: ResampleContext,
            samples: Array<IntArray>,
            length: Int,
            gain: Double,
            isLast: Boolean
        ): Int {
            var nsmplwrt: Int
            var writeLen: Int
            val dbps = if (rCtx.twopass) 8 else if (rCtx.dstFloat) 4 else rCtx.dbps
            var toberead = ((rCtx.nb2 - rCtx.rps - 1) / rCtx.osf + 1)

            if (rCtx.inbuflen + length < toberead && !isLast) {
                rCtx.inbuflen += fillInBuf(rCtx, samples, 0, length)
                rCtx.sumread += length.toLong()
                return 0
            }

            if (length == 0 && rCtx.inbuflen > 0 && isLast) {
                rCtx.inbuf.fill(0.0, rCtx.inbuflen * rCtx.nch, toberead * rCtx.nch)
                nsmplwrt = downSample(rCtx)
                rCtx.inbuflen = 0
                rCtx.rp += nsmplwrt * (rCtx.fs2 / rCtx.dfrq)
                rCtx.outBuffer!!.clear()
                fillOutBuf(rCtx, dbps, gain, nsmplwrt)
                rCtx.outBuffer!!.flip()
                return writeOutBytes(rCtx, nsmplwrt, dbps, 0, true)
            }

            var outBytesWritten = 0
            var lenUsed = 0

            while (lenUsed < length) {
                toberead = ((rCtx.nb2 - rCtx.rps - 1) / rCtx.osf + 1) - rCtx.inbuflen

                if (length - lenUsed < toberead) {
                    if (!isLast) {
                        rCtx.inbuflen += fillInBuf(rCtx, samples, lenUsed, length - lenUsed)
                        rCtx.sumread += (length - lenUsed).toLong()
                        return outBytesWritten
                    }
                    rCtx.inbuf.fill(0.0, rCtx.inbuflen * rCtx.nch, (toberead + rCtx.inbuflen) * rCtx.nch)
                    toberead = length - lenUsed
                }
                rCtx.inbuflen += fillInBuf(rCtx, samples, lenUsed, toberead)
                lenUsed += toberead

                rCtx.sumread += toberead.toLong()

                nsmplwrt = downSample(rCtx)
                rCtx.inbuflen = 0
                rCtx.rp += nsmplwrt * rCtx.fs2dfrq

                rCtx.outBuffer!!.clear()
                fillOutBuf(rCtx, dbps, gain, nsmplwrt)
                rCtx.outBuffer!!.flip()

                writeLen = writeOutBytes(rCtx, nsmplwrt, dbps, outBytesWritten, isLast)
                if (writeLen < 0) {
                    break
                }
                outBytesWritten += writeLen

                rCtx.sumwrite += (writeLen / rCtx.wbpf).toLong()

                rCtx.ds = (rCtx.rp - 1) / rCtx.fs2fs1

                if (rCtx.ds > rCtx.nb2) {
                    rCtx.ds = rCtx.nb2
                }
                var ch = 0
                while (ch < rCtx.nch) {
                    System.arraycopy(rCtx.buf2[ch], rCtx.ds, rCtx.buf2[ch], 0, rCtx.nx + 1 + rCtx.nb2 - rCtx.ds)
                    ch++
                }

                rCtx.rp -= rCtx.ds * rCtx.fs2fs1

                ch = 0
                while (ch < rCtx.nch) {
                    System.arraycopy(rCtx.buf1[ch], rCtx.nb2, rCtx.buf2[ch], rCtx.nx + 1, rCtx.nb2)
                    ch++
                }
            }
            return outBytesWritten
        }*/

        /*private fun no_src(rCtx: ResampleContext, samples: Array<IntArray>, length: Int, gain: Double): Int {
            var i: Int
            var ch: Int
            var f: Double
            var p: Double
            var len: Int

            var outBytesWritten = 0
            var lenUsed = 0

            var j = 0
            if (rCtx.nch == 1 && rCtx.rnch != rCtx.nch) {
                j = rCtx.mono
            }

            while (lenUsed < length) {
                len = length - lenUsed
                rCtx.outBuffer!!.clear()
                if (len > rCtx.outBuffer!!.limit() / rCtx.nch) {
                    len = rCtx.outBuffer!!.limit() / rCtx.nch
                }
                lenUsed += len

                if (rCtx.twopass) {
                    i = 0
                    while (i < len) {
                        ch = 0
                        while (ch < rCtx.nch) {
                            f = intSampleToDouble(rCtx, samples[ch % rCtx.nch + j][i]) * gain
                            p = if (f > 0) f else -f
                            rCtx.peak = if (rCtx.peak < p) p else rCtx.peak
                            rCtx.outBuffer!!.putDouble(f)
                            ch++
                        }
                        i++
                    }
                } else if (rCtx.dbps == rCtx.bps && !rCtx.dstFloat) {
                    i = 0
                    while (i < len) {
                        ch = 0
                        while (ch < rCtx.dnch) {
                            writeIntToBuffer(rCtx, samples[ch % rCtx.nch + j][i], gain)
                            ch++
                        }
                        i++
                    }
                } else {
                    i = 0
                    while (i < len) {
                        ch = 0
                        while (ch < rCtx.dnch) {
                            f = intSampleToDouble(rCtx, samples[ch % rCtx.nch + j][i]) * gain
                            writeToOutBuffer(rCtx, f, (ch % rCtx.nch))
                            ch++
                        }
                        i++
                    }
                }
                rCtx.outBuffer!!.flip()
                if (rCtx.outBytes!!.size - outBytesWritten < rCtx.outBuffer!!.limit()) {
                    val tmpBytes = ByteArray(outBytesWritten + rCtx.outBuffer!!.limit())
                    System.arraycopy(rCtx.outBytes!!, 0, tmpBytes, 0, outBytesWritten)
                    rCtx.outBytes = tmpBytes
                }
                rCtx.outBuffer!![rCtx.outBytes, outBytesWritten, rCtx.outBuffer!!.limit()]
                outBytesWritten += rCtx.outBuffer!!.limit()
            }
            return outBytesWritten
        }*/

        /* end int[][] input */ /* byte[] input */
        private fun upsample(
            rCtx: ResampleContext,
            samples: ByteArray,
            offset: Int,
            length: Int,
            gain: Double,
            isLast: Boolean
        ): Int {
            var nsmplread: Int
            var nsmplwrt1: Int
            var nsmplwrt2: Int
            var writeLen: Int
            val dbps = if (rCtx.twopass) 8 else if (rCtx.dstFloat) 4 else rCtx.dbps
            val tobereadbase = floor(rCtx.nb2.toDouble() * rCtx.sfrq / (rCtx.dfrq * rCtx.osf)).toInt() + 1 + rCtx.nx
            var toberead = tobereadbase - rCtx.inbuflen

            if (rCtx.inBuffer!!.position() + length < toberead * rCtx.bpf && !isLast) {
                rCtx.inBuffer!!.put(samples, offset, length)
                return 0
            }

            if (length == 0 && rCtx.inBuffer!!.hasRemaining() && isLast) {
                nsmplread = rCtx.inBuffer!!.position() / rCtx.bpf
                rCtx.inBuffer!!.flip()
                fillInBuf(rCtx, nsmplread)
                rCtx.inbuf.fill(
                    0.0,
                    rCtx.nch * (rCtx.inbuflen + nsmplread),
                    rCtx.nch * (rCtx.inbuflen + toberead)
                )
                rCtx.inBuffer!!.clear()
                rCtx.inbuflen += toberead
                rCtx.sumread += nsmplread.toLong()
                nsmplwrt1 = rCtx.nb2
                rCtx.ip = ((rCtx.sfrq * (rCtx.rp - 1) + rCtx.fs1) / rCtx.fs1) * rCtx.nch
                nsmplwrt2 = upSample(rCtx, nsmplwrt1)
                rCtx.rp += nsmplwrt1 * rCtx.sfrqfrqgcd / rCtx.osf
                rCtx.outBuffer!!.clear()
                fillOutBuf(rCtx, dbps, gain, nsmplwrt2)
                rCtx.outBuffer!!.flip()
                return writeOutBytes(rCtx, nsmplwrt2, dbps, 0, true)
            }

            var outBytesWritten = 0
            var lenUsed = 0

            while (lenUsed < length) {
                toberead = tobereadbase - rCtx.inbuflen
                nsmplread = toberead * rCtx.bpf - rCtx.inBuffer!!.position()
                if (nsmplread > length - lenUsed) {
                    nsmplread = length - lenUsed
                }

                rCtx.inBuffer!!.put(samples, offset + lenUsed, nsmplread)
                lenUsed += nsmplread

                if (rCtx.inBuffer!!.position() < toberead * rCtx.bpf) {
                    if (!isLast) return outBytesWritten
                    nsmplread = rCtx.inBuffer!!.position() / rCtx.bpf
                    rCtx.inbuf.fill(
                        0.0,
                        rCtx.nch * (rCtx.inbuflen + nsmplread),
                        rCtx.nch * (rCtx.inbuflen + toberead)
                    )
                    toberead = nsmplread
                }

                rCtx.inBuffer!!.flip()
                fillInBuf(rCtx, toberead)
                rCtx.inBuffer!!.clear()

                rCtx.inbuflen += toberead

                rCtx.sumread += toberead.toLong()

                nsmplwrt1 = rCtx.nb2

                rCtx.ip = ((rCtx.sfrq * (rCtx.rp - 1) + rCtx.fs1) / rCtx.fs1) * rCtx.nch

                nsmplwrt2 = upSample(rCtx, nsmplwrt1)

                rCtx.rp += nsmplwrt1 * rCtx.sfrqfrqgcd / rCtx.osf

                rCtx.outBuffer!!.clear()
                fillOutBuf(rCtx, dbps, gain, nsmplwrt2)
                rCtx.outBuffer!!.flip()

                writeLen = writeOutBytes(rCtx, nsmplwrt2, dbps, outBytesWritten, isLast)
                if (writeLen < 0) {
                    break
                }
                outBytesWritten += writeLen

                rCtx.sumwrite += (writeLen / rCtx.wbpf).toLong()

                rCtx.ds = (rCtx.rp - 1) / rCtx.fs1sfrq

                if (rCtx.inbuflen > rCtx.ds) System.arraycopy(
                    rCtx.inbuf,
                    rCtx.nch * rCtx.ds,
                    rCtx.inbuf,
                    0,
                    rCtx.nch * (rCtx.inbuflen - rCtx.ds)
                )

                rCtx.inbuflen -= rCtx.ds
                rCtx.rp -= rCtx.ds * rCtx.fs1sfrq
            }
            return outBytesWritten
        }

        private fun downsample(
            rCtx: ResampleContext,
            samples: ByteArray,
            offset: Int,
            length: Int,
            gain: Double,
            isLast: Boolean
        ): Int {
            var nsmplread: Int
            var nsmplwrt: Int
            var writeLen: Int
            val dbps = if (rCtx.twopass) 8 else if (rCtx.dstFloat) 4 else rCtx.dbps
            var toberead = ((rCtx.nb2 - rCtx.rps - 1) / rCtx.osf + 1)

            if (rCtx.inBuffer!!.position() + length < toberead * rCtx.bpf && !isLast) {
                rCtx.inBuffer!!.put(samples, offset, length)
                return 0
            }

            if (length == 0 && rCtx.inBuffer!!.hasRemaining() && isLast) {
                nsmplread = rCtx.inBuffer!!.position() / (rCtx.bpf)
                rCtx.inBuffer!!.flip()
                fillInBuf(rCtx, nsmplread)
                rCtx.inbuf.fill(0.0, nsmplread * rCtx.nch, toberead * rCtx.nch)
                rCtx.inBuffer!!.clear()
                rCtx.sumread += nsmplread.toLong()
                nsmplwrt = downSample(rCtx)
                rCtx.rp += nsmplwrt * (rCtx.fs2 / rCtx.dfrq)
                rCtx.outBuffer!!.clear()
                fillOutBuf(rCtx, dbps, gain, nsmplwrt)
                rCtx.outBuffer!!.flip()
                return writeOutBytes(rCtx, nsmplwrt, dbps, 0, true)
            }

            var outBytesWritten = 0
            var lenUsed = 0

            while (lenUsed < length) {
                toberead = ((rCtx.nb2 - rCtx.rps - 1) / rCtx.osf + 1)
                nsmplread = toberead * rCtx.bpf - rCtx.inBuffer!!.position()
                if (nsmplread > length - lenUsed) {
                    nsmplread = length - lenUsed
                }
                rCtx.inBuffer!!.put(samples, offset + lenUsed, nsmplread)
                lenUsed += nsmplread

                if (rCtx.inBuffer!!.position() < toberead * rCtx.bpf) {
                    if (!isLast) return outBytesWritten
                    nsmplread = rCtx.inBuffer!!.position() / rCtx.bpf
                    rCtx.inbuf.fill(
                        0.0,
                        rCtx.nch * (rCtx.inbuflen + nsmplread),
                        rCtx.nch * (rCtx.inbuflen + toberead)
                    )
                    toberead = nsmplread
                }

                rCtx.inBuffer!!.flip()
                fillInBuf(rCtx, toberead)
                rCtx.inBuffer!!.clear()

                rCtx.sumread += toberead.toLong()

                nsmplwrt = downSample(rCtx)
                rCtx.rp += nsmplwrt * rCtx.fs2dfrq

                rCtx.outBuffer!!.clear()
                fillOutBuf(rCtx, dbps, gain, nsmplwrt)
                rCtx.outBuffer!!.flip()

                writeLen = writeOutBytes(rCtx, nsmplwrt, dbps, outBytesWritten, isLast)
                if (writeLen < 0) {
                    break
                }
                outBytesWritten += writeLen

                rCtx.sumwrite += (writeLen / rCtx.wbpf).toLong()

                rCtx.ds = (rCtx.rp - 1) / rCtx.fs2fs1

                if (rCtx.ds > rCtx.nb2) {
                    rCtx.ds = rCtx.nb2
                }
                var ch = 0
                while (ch < rCtx.nch) {
                    System.arraycopy(rCtx.buf2[ch], rCtx.ds, rCtx.buf2[ch], 0, rCtx.nx + 1 + rCtx.nb2 - rCtx.ds)
                    ch++
                }

                rCtx.rp -= rCtx.ds * rCtx.fs2fs1

                ch = 0
                while (ch < rCtx.nch) {
                    System.arraycopy(rCtx.buf1[ch], rCtx.nb2, rCtx.buf2[ch], rCtx.nx + 1, rCtx.nb2)
                    ch++
                }
            }
            return outBytesWritten
        }

        private fun no_src(rCtx: ResampleContext, samples: ByteArray, offset: Int, length: Int, gain: Double): Int {
            var i: Int
            var ch: Int
            var f: Double
            var p: Double
            var len = length

            if (len >= rCtx.inBuffer!!.remaining()) {
                len = rCtx.inBuffer!!.remaining()
            }

            if (rCtx.inBuffer!!.position() + len < rCtx.bps * rCtx.nch) {
                rCtx.inBuffer!!.put(samples, offset, len)
                return 0
            }

            var outBytesWritten = 0
            var lenUsed = 0

            var j = 0
            if (rCtx.nch == 1 && rCtx.rnch != rCtx.nch) {
                j = rCtx.mono
            }

            while (lenUsed < length) {
                len = rCtx.inBuffer!!.remaining()
                if (len > length - lenUsed) {
                    len = length - lenUsed
                }

                rCtx.inBuffer!!.put(samples, offset + lenUsed, len)

                if (rCtx.inBuffer!!.position() < rCtx.bps * rCtx.nch) {
                    break
                }

                rCtx.inBuffer!!.flip()
                rCtx.outBuffer!!.clear()

                lenUsed += len

                if (rCtx.twopass) {
                    i = 0
                    while (i < rCtx.inBuffer!!.limit() - rCtx.bps * rCtx.rnch) {
                        ch = 0
                        while (ch < rCtx.nch) {
                            f = readFromInBuffer(rCtx, i + (ch + j) * rCtx.bps) * gain
                            p = if (f > 0) f else -f
                            rCtx.peak = if (rCtx.peak < p) p else rCtx.peak
                            rCtx.outBuffer!!.putDouble(f)
                            ch++
                        }
                        i += rCtx.bps * rCtx.rnch
                    }
                } else {
                    i = 0
                    while (i < rCtx.inBuffer!!.limit() - rCtx.bps * rCtx.rnch) {
                        ch = 0
                        while (ch < rCtx.dnch) {
                            f = readFromInBuffer(rCtx, i + ((ch % rCtx.nch) + j) * rCtx.bps) * gain
                            writeToOutBuffer(rCtx, f, (ch % rCtx.nch))
                            ch++
                        }
                        i += rCtx.bps * rCtx.rnch
                    }
                }
                rCtx.inBuffer!!.position(i)
                rCtx.inBuffer!!.compact()
                rCtx.outBuffer!!.flip()
                if (rCtx.outBytes!!.size - outBytesWritten < rCtx.outBuffer!!.limit()) {
                    val tmpBytes = ByteArray(outBytesWritten + rCtx.outBuffer!!.limit())
                    System.arraycopy(rCtx.outBytes!!, 0, tmpBytes, 0, outBytesWritten)
                    rCtx.outBytes = tmpBytes
                }
                rCtx.outBuffer!![rCtx.outBytes, outBytesWritten, rCtx.outBuffer!!.limit()]
                outBytesWritten += rCtx.outBuffer!!.limit()
            }
            return outBytesWritten
        }

        /* end Stream input/output */
        private fun readFromInBuffer(rCtx: ResampleContext, i: Int): Double {
            if (rCtx.srcFloat) {
                return rCtx.inBuffer!!.getDouble(i)
            } else {
                when (rCtx.bps) {
                    1 -> return NORMALIZE_FACTOR_8 * ((rCtx.inBuffer!![i].toShort().toInt() and 0xff) - 128).toDouble()
                    2 -> return NORMALIZE_FACTOR_16 * rCtx.inBuffer!!.getShort(i)
                    3 -> return if (rCtx.srcByteOrder == ByteOrder.LITTLE_ENDIAN) {
                        NORMALIZE_FACTOR_24 * ((rCtx.inBuffer!!.getShort(i)
                            .toInt() and 0xffff) or ((rCtx.inBuffer!![i + 2].toInt() shl 24) shr 8)).toDouble()
                    } else {
                        NORMALIZE_FACTOR_24 * (((rCtx.inBuffer!![i].toInt() shl 24) shr 8) or (rCtx.inBuffer!!.getShort(
                            i + 1
                        ).toInt() and 0xffff)).toDouble()
                    }

                    4 -> return NORMALIZE_FACTOR_32 * rCtx.inBuffer!!.getInt(i).toDouble()
                }
            }
            return 0.0
        }

        /*private fun intSampleToDouble(rCtx: ResampleContext, sample: Int): Double {
            when (rCtx.bps) {
                1 -> return NORMALIZE_FACTOR_8 * ((sample and 0xff) - 128).toDouble()
                2 -> return NORMALIZE_FACTOR_16 * sample
                3 -> return NORMALIZE_FACTOR_24 * sample
                4 -> return NORMALIZE_FACTOR_32 * sample
            }
            return 0.0
        }*/

        private fun writeToOutBuffer(rCtx: ResampleContext, f: Double, ch: Int) {
            var f1 = f
            var s: Int
            if (rCtx.dstFloat) {
                rCtx.outBuffer!!.putFloat(f1.toFloat())
            } else {
                when (rCtx.dbps) {
                    1 -> {
                        f1 *= (0x7f).toDouble()
                        s = if (rCtx.dither > 0) do_shaping(rCtx, f1, ch) else RINT(f1)
                        rCtx.outBuffer!!.put((s + 128).toByte())
                    }

                    2 -> {
                        f1 *= (0x7fff).toDouble()
                        s = if (rCtx.dither > 0) do_shaping(rCtx, f1, ch) else RINT(f1)
                        rCtx.outBuffer!!.putShort(s.toShort())
                    }

                    3 -> {
                        f1 *= (0x7fffff).toDouble()
                        s = if (rCtx.dither > 0) do_shaping(rCtx, f1, ch) else RINT(f1)
                        rCtx.outBuffer!!.putShort(s.toShort())
                        s = s shr 16
                        rCtx.outBuffer!!.put(s.toByte())
                    }
                }
            }
        }

        /*private fun writeIntToBuffer(rCtx: ResampleContext, sample: Int, gain: Double) {
            var s = (sample * gain).toInt()
            when (rCtx.dbps) {
                1 -> rCtx.outBuffer!!.put(s.toByte())
                2 -> rCtx.outBuffer!!.putShort(s.toShort())
                3 -> {
                    rCtx.outBuffer!!.putShort(s.toShort())
                    s = s shr 16
                    rCtx.outBuffer!!.put(s.toByte())
                }
            }
        }*/

        /*fun dBToGain(att: Double): Double {
            return 10.0.pow(att / 20)
        }*/
    }
}
