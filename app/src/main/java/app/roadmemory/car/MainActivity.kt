package app.roadmemory.car

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import app.roadmemory.car.gleo.GleoBridge
import app.roadmemory.car.ui.RoadMemoryApp

class MainActivity : ComponentActivity() {
    private val controller: DriveController by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { RoadMemoryApp(controller) }
        if (savedInstanceState == null) GleoBridge.handle(this, intent)   // Gleo가 앱을 열며 부른 함수
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        GleoBridge.handle(this, intent)
    }
}
