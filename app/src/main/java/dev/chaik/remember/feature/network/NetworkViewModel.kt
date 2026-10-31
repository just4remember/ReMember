package dev.chaik.remember.feature.network

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.chaik.remember.app.AppContainer
import dev.chaik.remember.core.network.NetworkStatus
import dev.chaik.remember.core.network.NetworkType
import dev.chaik.remember.core.network.SiteResult
import dev.chaik.remember.core.network.SpeedPhase
import dev.chaik.remember.core.network.SpeedResult
import dev.chaik.remember.core.network.SpeedTester
import dev.chaik.remember.core.network.WhitelistCheck
import dev.chaik.remember.core.network.WhitelistVerdict
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import dev.chaik.remember.core.network.PublicIp
import dev.chaik.remember.core.network.PublicIps
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException

/** Что сейчас с замером скорости. */
sealed interface SpeedState {
    data object Idle : SpeedState

    /**
     * [value] — пинг в мс или скорость в Мбит/с; [fraction] — сколько фазы пройдено (0…1);
     * [ping]/[download] — уже измеренное.
     */
    data class Running(
        val phase: SpeedPhase,
        val value: Double,
        val fraction: Float = 0f,
        val ping: Double? = null,
        val download: Double? = null,
    ) : SpeedState
    data class Done(val result: SpeedResult) : SpeedState
    data object Failed : SpeedState
}

/** Страница «Сеть»: текущая сеть, замер скорости и проверка белых списков. */
class NetworkViewModel(container: AppContainer) : ViewModel() {
    val store = container.networkSettings
    private val tester = SpeedTester()

    val network: StateFlow<NetworkStatus> = container.networkMonitor.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), container.networkMonitor.current())

    var speed by mutableStateOf<SpeedState>(SpeedState.Idle)
        private set
    private var speedJob: Job? = null

    /** Результаты проверки по сайтам; нет в словаре — ещё не проверяли, null — проверяется. */
    val sites = mutableStateMapOf<String, SiteResult?>()
    var verdict by mutableStateOf<WhitelistVerdict?>(null)
        private set
    var checking by mutableStateOf(false)
        private set

    /** Внешние IP; пусто — ещё не узнали или не удалось (см. [ipLoading]). */
    var publicIp by mutableStateOf(PublicIps())
        private set
    var ipLoading by mutableStateOf(true)
        private set

    val hasNetwork: Boolean get() = network.value.type != NetworkType.NONE

    init {
        // Сменилась сеть или включили VPN — адрес другой
        viewModelScope.launch {
            network.map { it.type to it.vpn }.distinctUntilChanged().collectLatest { (type, _) ->
                publicIp = PublicIps()
                ipLoading = type != NetworkType.NONE
                if (type == NetworkType.NONE) return@collectLatest
                delay(IP_SETTLE_MS)
                publicIp = PublicIp.fetch()
                ipLoading = false
            }
        }
    }

    fun startSpeedTest() {
        speedJob?.cancel()
        speedJob = viewModelScope.launch {
            var ping: Double? = null
            var download: Double? = null
            speed = SpeedState.Running(SpeedPhase.PING, 0.0)
            try {
                val settings = store.settings.value
                val result = tester.run(settings.server, settings.speedOptions(network.value.metered)) { phase, value, fraction ->
                    // Фаза сменилась — запоминаем итог прошлой, чтобы показывать его рядом
                    val current = speed as? SpeedState.Running
                    if (current != null && current.phase != phase) {
                        if (current.phase == SpeedPhase.PING) ping = current.value
                        if (current.phase == SpeedPhase.DOWNLOAD) download = current.value
                    }
                    speed = SpeedState.Running(phase, value, fraction, ping, download)
                }
                speed = SpeedState.Done(result)
            } catch (e: CancellationException) {
                speed = SpeedState.Idle
                throw e
            } catch (e: IOException) {
                Log.w(TAG, "Замер не удался", e)
                speed = SpeedState.Failed
            } catch (e: IllegalArgumentException) {
                // Свой адрес сервера записан с ошибкой
                Log.w(TAG, "Замер не удался", e)
                speed = SpeedState.Failed
            }
        }
    }

    fun stopSpeedTest() {
        speedJob?.cancel()
        speedJob = null
    }

    fun checkWhitelist() {
        if (checking) return
        val settings = store.settings.value
        checking = true
        verdict = null
        sites.clear()
        (settings.allowedSites + settings.otherSites).forEach { sites[it] = null }
        viewModelScope.launch {
            try {
                // Каждый сайт показываем, как только он ответил, — не ждём самого медленного
                val results = WhitelistCheck.check(settings.allowedSites + settings.otherSites) { result ->
                    // Ответы приходят из разных потоков — в состояние экрана пишем с главного
                    viewModelScope.launch(Dispatchers.Main) { sites[result.host] = result }
                }.associateBy { it.host }
                results.forEach { (host, result) -> sites[host] = result }
                verdict = WhitelistCheck.verdict(
                    allowed = settings.allowedSites.mapNotNull(results::get),
                    others = settings.otherSites.mapNotNull(results::get),
                )
            } finally {
                checking = false
            }
        }
    }

    fun addSite(allowed: Boolean, input: String): Boolean {
        val host = WhitelistCheck.normalizeHost(input) ?: return false
        store.update { s ->
            if (allowed) s.copy(allowedSites = (s.allowedSites + host).distinct()) else s.copy(otherSites = (s.otherSites + host).distinct())
        }
        return true
    }

    /** Больше не спрашивать перед замером по мобильному интернету (вернуть — в «Страницах»). */
    fun stopAskingOnMobile() = store.update { it.copy(askOnMobile = false) }

    fun removeSite(host: String) {
        store.update { s -> s.copy(allowedSites = s.allowedSites - host, otherSites = s.otherSites - host) }
        sites.remove(host)
    }

    private companion object {
        /** Новая сеть не сразу готова: чуть ждём перед запросом адреса. */
        const val IP_SETTLE_MS = 500L

        const val TAG = "Network"
    }
}
