package dev.chaik.remember.feature.games.raskobki

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.navigation3.runtime.NavKey
import dev.chaik.remember.R
import dev.chaik.remember.appContainer
import dev.chaik.remember.core.ui.components.ScreenScaffold
import kotlinx.serialization.Serializable

@Serializable
data object RaskobkiRoute : NavKey

private const val SITE_URL = "https://raskobki.app/"
private const val SITE_HOST = "raskobki.app"

/**
 * Ра[скобки] — официальный сайт игры внутри приложения: без подмены интерфейса и запросов,
 * с его рекламой и правилами. Ссылки на другие сайты открываются в браузере.
 */
@OptIn(ExperimentalLayoutApi::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun RaskobkiScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lock = context.appContainer.appLock
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var canGoBack by remember { mutableStateOf(false) }
    var web by remember { mutableStateOf<WebView?>(null) }

    fun openExternally(uri: Uri) {
        // Браузер — не повод блокировать приложение
        lock.allowExternal()
        openInBrowser(context, uri)
    }

    // «Назад» сначала идёт по страницам сайта
    BackHandler(enabled = canGoBack) { web?.goBack() }

    ScreenScaffold(
        title = stringResource(R.string.game_raskobki),
        modifier = modifier,
        onBack = onBack,
        actions = {
            IconButton(onClick = { openExternally(web?.url?.toUri() ?: SITE_URL.toUri()) }) {
                Icon(Icons.AutoMirrored.Outlined.OpenInNew, stringResource(R.string.game_raskobki_open_site))
            }
        },
    ) { padding ->
        // С клавиатурой островка нет: страница поджимается только над клавиатурой
        val keyboard = WindowInsets.isImeVisible
        Box(
            Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .then(if (keyboard) Modifier.imePadding() else Modifier.padding(bottom = padding.calculateBottomPadding())),
        ) {
            AndroidView(
                factory = { viewContext ->
                    WebView(viewContext).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                val uri = request.url
                                val own = uri.host == SITE_HOST || uri.host == "www.$SITE_HOST"
                                if (own || !request.isForMainFrame) return false
                                openExternally(uri)
                                return true
                            }

                            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                                loading = true
                                failed = false
                            }

                            override fun onPageFinished(view: WebView, url: String?) {
                                loading = false
                                canGoBack = view.canGoBack()
                            }

                            override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                                canGoBack = view.canGoBack()
                            }

                            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                                if (request.isForMainFrame) failed = true
                            }
                        }
                        loadUrl(SITE_URL)
                        web = this
                    }
                },
                onRelease = { it.destroy() },
                modifier = Modifier.fillMaxSize(),
            )
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
            if (failed) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                ) {
                    Text(
                        text = stringResource(R.string.game_raskobki_error),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                    )
                    Button(onClick = { web?.reload() }) { Text(stringResource(R.string.game_raskobki_retry)) }
                }
            }
        }
    }
}

private fun openInBrowser(context: Context, uri: Uri) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (_: ActivityNotFoundException) {
        // Браузера нет — остаёмся на месте
    }
}
