package dev.chaik.remember.feature.notes.editor

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dev.chaik.remember.R
import dev.chaik.remember.appContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Очереди заметок и состояние их редакторов обновляются на главном потоке. */
internal class NoteMediaImports(private val context: Context, private val scope: CoroutineScope) {
    private val active = mutableMapOf<Job, () -> Long>()
    val running = MutableStateFlow(false)

    suspend fun await(noteId: Long) {
        active.entries.firstOrNull { it.value() == noteId }?.key?.join()
    }

    fun start(uris: List<Uri>, noteId: () -> Long, import: suspend () -> Unit) {
        val job = scope.launch(Dispatchers.Main.immediate, start = CoroutineStart.LAZY) {
            try {
                import()
            } finally {
                active.remove(coroutineContext[Job])
                running.value = active.isNotEmpty()
            }
        }
        active[job] = noteId
        running.value = true
        // ClipData передаёт службе доступ к выбранным файлам на время её работы.
        val intent = Intent(context, NoteMediaImportService::class.java).apply {
            clipData = ClipData.newRawUri(null, uris.first()).apply {
                uris.drop(1).forEach { addItem(ClipData.Item(it)) }
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            ContextCompat.startForegroundService(context, intent)
        } catch (e: IllegalStateException) {
            Log.w("NoteMediaImport", "Не удалось запустить службу импорта", e)
        } catch (e: SecurityException) {
            Log.w("NoteMediaImport", "Не удалось передать доступ к медиа службе", e)
        }
        job.start()
    }

    fun cancel() {
        active.keys.toList().forEach { it.cancel() }
    }
}

/** Android оставляет процесс активным, пока пользователь ждёт завершения импорта в фоне. */
class NoteMediaImportService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watcher: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val title = getString(R.string.editor_media_importing)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, title, NotificationManager.IMPORTANCE_LOW),
        )
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_gallery)
            .setContentTitle(title)
            .setProgress(0, 0, true)
            .setOngoing(true)
            .build()
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        watcher?.cancel()
        watcher = scope.launch {
            appContainer.noteMediaImports.running.first { !it }
            stopSelfResult(startId)
        }
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        appContainer.noteMediaImports.cancel()
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val CHANNEL = "note_media_import"
        const val NOTIFICATION_ID = 49_120
    }
}
