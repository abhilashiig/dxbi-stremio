package com.abhilash.matinee.tv.report

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HardwareCodecNameTest {
    @Test fun hardwareDecoders() {
        listOf("OMX.MTK.VIDEO.DECODER.HEVC", "OMX.amlogic.hevc.decoder.awesome2", "c2.mtk.hevc.decoder", "OMX.dolby.ac3.decoder")
            .forEach { assertTrue(it, isHardwareCodecName(it)) }
    }

    @Test fun softwareDecoders() {
        listOf("OMX.google.h264.decoder", "c2.android.av1.decoder", "OMX.ffmpeg.ac3.decoder", "c2.vendor.hevc.sw", "OMX.foo.sw.dec")
            .forEach { assertFalse(it, isHardwareCodecName(it)) }
    }
}
