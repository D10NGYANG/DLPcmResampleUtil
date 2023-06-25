package com.d10ng.pcmresample

import com.d10ng.pcmresample.constant.ChannelType
import com.d10ng.pcmresample.constant.EncodingType
import com.d10ng.pcmresample.ssrc.JavaSSRC
import java.io.*
import java.nio.ByteOrder

object DLPcmResampleUtil {
    /**
     * 重采样，保持通道数、位深、编码方式不变
     * - 录音文件时长越长，处理越耗时，不要在UI线程进行操作
     * @param srcPath String 源文件路径
     * @param targetPath String 目标文件路径
     * @param srcSampleRate Int 源采样率
     * @param targetSampleRate Int 目标采样率
     * @param channelType ChannelType 通道数
     * @param encodingType EncodingType 位深
     * @throws IOException if there is an error reading or writing the file
     */
    @Throws(IOException::class)
    fun resample(
        srcPath: String,
        targetPath: String,
        srcSampleRate: Int,
        targetSampleRate: Int,
        channelType: ChannelType = ChannelType.MONO,
        encodingType: EncodingType = EncodingType.PCM_16BIT
    ) {
        val srcData = File(srcPath).readBytes()
        val targetData = resample(srcData, srcSampleRate, targetSampleRate, channelType, encodingType)
        File(targetPath).writeBytes(targetData)
    }

    /**
     * 重采样，保持通道数、位深、编码方式不变
     * - 录音文件时长越长，处理越耗时，不要在UI线程进行操作
     * @param srcData ByteArray 源文件数据
     * @param srcSampleRate Int 源采样率
     * @param targetSampleRate Int 目标采样率
     * @param channelType ChannelType 通道数
     * @param encodingType EncodingType 位深
     * @return ByteArray 重采样后的数据
     */
    fun resample(
        srcData: ByteArray,
        srcSampleRate: Int,
        targetSampleRate: Int,
        channelType: ChannelType = ChannelType.MONO,
        encodingType: EncodingType = EncodingType.PCM_16BIT
    ): ByteArray {
        ByteArrayInputStream(srcData).use { fis ->
            ByteArrayOutputStream().use { fos ->
                val ssrc = JavaSSRC().apply {
                    setSrcChannels(channelType.intValue)
                    setDstChannels(channelType.intValue)
                    setMonoChannel(-1)
                    setSrcByteOrder(ByteOrder.LITTLE_ENDIAN)
                    setSrcBPS(encodingType.intValue * 8)
                    setDstBPS(encodingType.intValue * 8)
                    setSrcSamplingRate(srcSampleRate)
                    setDstSamplingRate(targetSampleRate)
                }
                ssrc.initialize()
                var readLen: Int
                val buffer = ByteArray(4096)
                while (true) {
                    readLen = fis.read(buffer)
                    if (readLen < 0) {
                        readLen = ssrc.resample(buffer, 0, 0, true)
                        if (readLen > 0) {
                            fos.write(ssrc.outBytes, 0, readLen)
                        }
                        break
                    }
                    readLen = ssrc.resample(buffer, 0, readLen, false)
                    if (readLen > 0) {
                        fos.write(ssrc.outBytes, 0, readLen)
                    }
                }
                return fos.toByteArray()
            }
        }
    }
}