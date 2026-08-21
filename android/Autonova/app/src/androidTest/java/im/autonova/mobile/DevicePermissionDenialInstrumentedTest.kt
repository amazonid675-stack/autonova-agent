package im.autonova.mobile

import android.Manifest
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import im.autonova.mobile.data.VoiceAssistant
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DevicePermissionDenialInstrumentedTest {
    @Test fun voice_input_fails_closed_with_clear_guidance_when_microphone_permission_is_denied() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        runCatching { InstrumentationRegistry.getInstrumentation().uiAutomation.revokeRuntimePermission(context.packageName, Manifest.permission.RECORD_AUDIO) }
        var error = ""
        VoiceAssistant(context).listen(onPartial = {}, onFinal = {}, onError = { error = it })
        assertEquals("Microphone permission is required before voice input can start.", error)
    }
}
