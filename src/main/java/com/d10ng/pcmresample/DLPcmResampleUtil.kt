package com.d10ng.pcmresample

import com.d10ng.pcmresample.constant.ChannelType
import com.d10ng.pcmresample.constant.EncodingType
import com.d10ng.pcmresample.ssrc.SSRC
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.io.FileOutputStream

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
     */
    fun resample(
        srcPath: String,
        targetPath: String,
        srcSampleRate: Int,
        targetSampleRate: Int,
        channelType: ChannelType = ChannelType.MONO,
        encodingType: EncodingType = EncodingType.PCM_16BIT
    ) {
        val fis = FileInputStream(srcPath)
        val fos = FileOutputStream(targetPath)
        SSRC(fis, fos, srcSampleRate, targetSampleRate, encodingType.intValue, encodingType.intValue, channelType.intValue, Integer.MAX_VALUE, 0.0, 0, true)
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
        val fis = ByteArrayInputStream(srcData)
        val fos = ByteArrayOutputStream()
        SSRC(fis, fos, srcSampleRate, targetSampleRate, encodingType.intValue, encodingType.intValue, channelType.intValue, Integer.MAX_VALUE, 0.0, 0, true)
        return fos.toByteArray()
    }
}