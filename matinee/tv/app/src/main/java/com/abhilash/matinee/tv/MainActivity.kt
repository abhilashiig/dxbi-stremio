package com.abhilash.matinee.tv

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.abhilash.matinee.tv.report.ReportScreen
import com.abhilash.matinee.tv.report.deviceReport

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sections = deviceReport(this)
        // Readable over adb too: adb logcat -s MatineeReport
        sections.forEach { s -> Log.i("MatineeReport", "== ${s.title}"); s.lines.forEach { Log.i("MatineeReport", it) } }
        setContent { ReportScreen(sections) }
    }
}
