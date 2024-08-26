import com.d10ng.pcmresample.DLPcmResampleUtil
import com.d10ng.pcmresample.constant.ChannelType
import com.d10ng.pcmresample.constant.EncodingType
import com.d10ng.pcmresample.resample
import org.junit.Test
import java.io.File

class Test {

    private val basePath = "src/jvmTest/resources"

    /**
     * 测试文件重采样
     */
    @Test
    fun testFile() {
        val srcPath = "${basePath}/ori48.pcm"
        val targetPath = "./48to8.pcm"
        val srcSampleRate = 48000
        val targetSampleRate = 8000
        val channelType = ChannelType.MONO
        val encodingType = EncodingType.PCM_16BIT
        DLPcmResampleUtil.resample(srcPath, targetPath, srcSampleRate, targetSampleRate, channelType, encodingType)
        // 读取目标文件数据
        val targetData = File(targetPath).readBytes()
        // 获取resource目录下的目标文件数据
        val oriData = File("${basePath}/48to8.pcm").readBytes()
        assert(targetData.contentEquals(oriData))
        // 删除目标文件
        File(targetPath).delete()
    }

    /**
     * 测试数据重采样
     */
    @Test
    fun testByteArray() {
        val srcData = File("${basePath}/ori48.pcm").readBytes()
        val srcSampleRate = 48000
        val targetSampleRate = 8000
        val channelType = ChannelType.MONO
        val encodingType = EncodingType.PCM_16BIT
        val targetData = DLPcmResampleUtil.resample(srcData, srcSampleRate, targetSampleRate, channelType, encodingType)
        // 写入文件
        //File("./48to8.pcm").writeBytes(targetData)
        // 获取resource目录下的目标文件数据
        val oriData = File("${basePath}/48to8.pcm").readBytes()
        assert(targetData.contentEquals(oriData))
    }

    /**
     * 循环测试数据重采样，测试内存泄露问题
     */
    @Test
    fun testByteArrayLoop() {
        val srcData = File("${basePath}/ori48.pcm").readBytes()
        val srcSampleRate = 48000
        val targetSampleRate = 8000
        val channelType = ChannelType.MONO
        val encodingType = EncodingType.PCM_16BIT
        for (i in 0..3000) {
            DLPcmResampleUtil.resample(srcData, srcSampleRate, targetSampleRate, channelType, encodingType)
        }
    }
}