package ca.stefanm.mockktutorial

import android.app.Application
import android.util.Log
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MockKTutorialApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.d("MockKTutorialApp", "Starting App!")
    }
}