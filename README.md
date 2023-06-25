# DLPcmResampleUtil
PCM文件调整采样率，Java库

## 参考
- [hutm/JSSRC](https://github.com/hutm/JSSRC)
- [ideastudios/AndroidPcmResample](https://github.com/ideastudios/AndroidPcmResample)

## 版本
version = `0.0.1`

## 使用说明
### 1、添加仓库
```gradle
maven { url 'https://raw.githubusercontent.com/D10NGYANG/maven-repo/main/repository'}
```
### 2、添加依赖
```gradle
implementation 'com.github.D10NGYANG:DLPcmResampleUtil:$version'
```
### 3、混淆
```properties
-keep class com.d10ng.pcmresample.** {*;}
-dontwarn com.d10ng.pcmresample.**
```
### 4、使用
```kotlin
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
)
```
```kotlin
val srcPath = "./ori48.pcm"
val targetPath = "./48to8.pcm"
val srcSampleRate = 48000
val targetSampleRate = 8000
val channelType = ChannelType.MONO
val encodingType = EncodingType.PCM_16BIT
DLPcmResampleUtil.resample(srcPath, targetPath, srcSampleRate, targetSampleRate, channelType, encodingType)
```

## 后续计划
> 暂无