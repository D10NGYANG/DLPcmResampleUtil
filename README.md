# DLPcmResampleUtil
PCM文件调整采样率，kotlin multiplatform库，目前支持`Android`、`JVM`、`IOS`、`MacOS`、`JS`、`Linux`；

## 参考
- [hutm/JSSRC](https://github.com/hutm/JSSRC)
- [waynetam/JavaSSRC](https://github.com/waynetam/JavaSSRC)
- [ideastudios/AndroidPcmResample](https://github.com/ideastudios/AndroidPcmResample)

## 版本
version = `0.2.1`

## 使用说明
### 1、添加仓库
```gradle
maven { url 'https://raw.githubusercontent.com/D10NGYANG/maven-repo/main/repository'}
```
### 2、添加依赖
```gradle
implementation("com.github.D10NGYANG:DLPcmResampleUtil:$version")
// 通用计算工具
implementation("com.github.D10NGYANG:DLCommonUtil:0.4.1")
// 时间工具
implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
```
### 3、混淆
```properties
-keep class com.d10ng.pcmresample.** {*;}
-dontwarn com.d10ng.pcmresample.**
```
### 4、使用
#### 对文件进行重采样
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
#### 对字节数据进行重采样
```kotlin
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
): ByteArray
```
### 5、示例
> 参考 `Test` 文件：[Test.kt](src/test/java/Test.kt)；

## 后续计划
> 暂无