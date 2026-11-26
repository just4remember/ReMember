package dev.chaik.remember

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import dev.chaik.remember.app.ReMemberApp
import dev.chaik.remember.core.ui.EllipsisCapitalization
import dev.chaik.remember.core.ui.releaseFocus
import dev.chaik.remember.core.reminders.OpenTarget
import dev.chaik.remember.feature.lock.LockActivity

/** FragmentActivity — ради системного окна отпечатка (подтверждение в «Безопасности»). */
class MainActivity : FragmentActivity() {
    private val lock get() = appContainer.appLock

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Без полупрозрачной подложки под кнопками навигации — контент виден до самого низа
        window.isNavigationBarContrastEnforced = false
        setContent {
            // Свернули клавиатуру или нажали мимо полей — курсор уходит (у окон и шторок это своё)
            Box(Modifier.fillMaxSize().releaseFocus()) {
                // Заглавная буква после «...», как после точки
                EllipsisCapitalization { ReMemberApp(lock) }
            }
        }
        if (savedInstanceState == null) handleOpen(intent)
        askNotificationsOnce()
    }

    /** Тап по уведомлению, когда приложение уже открыто. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleOpen(intent)
    }

    private fun handleOpen(intent: Intent?) {
        OpenTarget.from(intent)?.let { appContainer.openRequests.value = it }
    }

    /** Разрешение на уведомления спрашиваем один раз; дальше — со страницы «Напоминания». */
    private fun askNotificationsOnce() {
        val store = appContainer.reminderSettings
        if (store.permissionAsked || appContainer.notifier.canPost) return
        store.permissionAsked = true
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
            .launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    override fun onStart() {
        super.onStart()
        appContainer.wordDictionaryUpdater.check()
        appContainer.notifier.clearOnAppOpen()
        // Вернулись из фона: если прошло больше таймаута — снова замок
        lock.onForeground()
        if (lock.locked.value) LockActivity.show(this)
    }
}
