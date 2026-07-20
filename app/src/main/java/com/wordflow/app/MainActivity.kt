package com.wordflow.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.wordflow.app.data.prefs.AppSettings
import com.wordflow.app.ui.AppRoot
import com.wordflow.app.ui.LocalAppContainer
import com.wordflow.app.ui.LocalHapticsEnabled
import com.wordflow.app.ui.LocalSoundEnabled
import com.wordflow.app.ui.theme.WordFlowTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as WordFlowApp).container

        // 首次启动时把内置词库导入数据库（幂等）
        lifecycleScope.launch {
            container.wordRepository.ensureSeeded()
        }

        setContent {
            CompositionLocalProvider(LocalAppContainer provides container) {
                val settings by container.settingsRepository.settings
                    .collectAsStateWithLifecycle(initialValue = AppSettings())

                WordFlowTheme(themeMode = settings.themeMode) {
                    CompositionLocalProvider(
                        LocalHapticsEnabled provides settings.hapticsEnabled,
                        LocalSoundEnabled provides settings.soundEnabled
                    ) {
                        AppRoot()
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            (application as? WordFlowApp)?.container?.ttsManager?.shutdown()
        }
    }
}
