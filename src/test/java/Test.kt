import com.d10ng.pcmresample.DLPcmResampleUtil
import com.d10ng.pcmresample.constant.ChannelType
import com.d10ng.pcmresample.constant.EncodingType
import org.junit.Test
import java.io.File

class Test {

    /**
     * 测试文件重采样
     */
    @Test
    fun testFile() {
        val srcPath = javaClass.classLoader.getResource("ori48.pcm")!!.path
        val targetPath = "./48to8.pcm"
        val srcSampleRate = 48000
        val targetSampleRate = 8000
        val channelType = ChannelType.MONO
        val encodingType = EncodingType.PCM_16BIT
        DLPcmResampleUtil.resample(srcPath, targetPath, srcSampleRate, targetSampleRate, channelType, encodingType)
        // 读取目标文件数据
        val targetData = File(targetPath).readBytes()
        // 获取resource目录下的目标文件数据
        val oriData = javaClass.classLoader.getResource("48to8.pcm")!!.readBytes()
        assert(targetData.contentEquals(oriData))
        // 删除目标文件
        File(targetPath).delete()
    }

    /**
     * 测试数据重采样
     */
    @Test
    fun testByteArray() {
        val srcData = javaClass.classLoader.getResource("ori48.pcm")!!.readBytes()
        val srcSampleRate = 48000
        val targetSampleRate = 8000
        val channelType = ChannelType.MONO
        val encodingType = EncodingType.PCM_16BIT
        val targetData = DLPcmResampleUtil.resample(srcData, srcSampleRate, targetSampleRate, channelType, encodingType)
        // 获取resource目录下的目标文件数据
        val oriData = javaClass.classLoader.getResource("48to8.pcm")!!.readBytes()
        assert(targetData.contentEquals(oriData))
    }

    /**
     * 循环测试数据重采样，测试内存泄露问题
     */
    @Test
    fun testByteArrayLoop() {
        val srcData = javaClass.classLoader.getResource("ori48.pcm")!!.readBytes()
        val srcSampleRate = 48000
        val targetSampleRate = 8000
        val channelType = ChannelType.MONO
        val encodingType = EncodingType.PCM_16BIT
        for (i in 0..3000) {
            DLPcmResampleUtil.resample(srcData, srcSampleRate, targetSampleRate, channelType, encodingType)
        }
    }
}