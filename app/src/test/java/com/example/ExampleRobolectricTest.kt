package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.domain.util.QatarFormatters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `app name matches ONE HOME PRO and Qatar defaults hold`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("ONE HOME PRO", appName)
        assertEquals("Asia/Qatar", QatarFormatters.qatarTimeZone.id)
        assertEquals("QAR 3,500.50", QatarFormatters.formatQar(350_050L))
        assertEquals(350_050L, QatarFormatters.parseQarToFils("3500.50"))
        assertTrue(QatarFormatters.periodKey().matches(Regex("\\d{4}-\\d{2}")))
    }
}
