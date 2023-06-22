import com.d10ng.pcmresample.DLPcmResampleUtil
import com.d10ng.pcmresample.constant.ChannelType
import com.d10ng.pcmresample.constant.EncodingType
import org.junit.Test

class Test {

    @Test
    fun test() {
        val srcPath = javaClass.classLoader.getResource("ori48.pcm")!!.path
        val targetPath = "/Users/d10ng/Downloads/48to8.pcm"
        val srcSampleRate = 48000
        val targetSampleRate = 8000
        val channelType = ChannelType.MONO
        val encodingType = EncodingType.PCM_16BIT
        DLPcmResampleUtil.resample(srcPath, targetPath, srcSampleRate, targetSampleRate, channelType, encodingType)
    }
}