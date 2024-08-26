package com.d10ng.pcmresample

import com.d10ng.pcmresample.constant.ChannelType
import com.d10ng.pcmresample.constant.EncodingType
import java.io.File
import java.io.IOException

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
fun DLPcmResampleUtil.resample(
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