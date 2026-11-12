package app.thelauncher.updates

import android.content.Context
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Where an update from GitHub is at. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val update: AppUpdate) : UpdateState
    data class Downloading(val update: AppUpdate, val progress: Float) : UpdateState
    data class NeedsPermission(val update: AppUpdate, val apk: File) : UpdateState
    data class Failed(val message: String, val update: AppUpdate? = null) : UpdateState
}

/** Checks for, downloads and installs updates; one per app process. */
class Updater private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val updates = AppUpdates(appContext)
    private val prefs = appContext.getSharedPreferences("app_updates", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** Looks for an update. Without [manual], at most about once a day and only shows something if there is one. */
    fun checkForUpdate(manual: Boolean) {
        if (job?.isActive == true) return
        if (!manual && System.currentTimeMillis() - prefs.getLong(KEY_LAST_CHECK, 0) < CHECK_EVERY_MS) return
        job = scope.launch {
            if (manual) _state.value = UpdateState.Checking
            try {
                val update = updates.check()
                prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
                _state.value = when {
                    update == null -> if (manual) UpdateState.UpToDate else UpdateState.Idle
                    !manual && prefs.getString(KEY_SKIPPED, null) == update.version -> UpdateState.Idle
                    else -> UpdateState.Available(update)
                }
            } catch (t: Throwable) {
                if (manual) _state.value = UpdateState.Failed(message(t))
            }
        }
    }

    fun startUpdate() {
        val update = when (val s = _state.value) {
            is UpdateState.Available -> s.update
            is UpdateState.Failed -> s.update
            else -> null
        } ?: return
        job?.cancel()
        job = scope.launch {
            _state.value = UpdateState.Downloading(update, 0f)
            try {
                val apk = updates.download(update) { p ->
                    _state.value = UpdateState.Downloading(update, p)
                }
                install(update, apk)
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                _state.value = UpdateState.Failed(message(t), update)
            }
        }
    }

    private fun install(update: AppUpdate, apk: File) {
        if (!updates.canInstall()) {
            _state.value = UpdateState.NeedsPermission(update, apk)
            return
        }
        try {
            updates.install(apk)
            _state.value = UpdateState.Idle
        } catch (t: Throwable) {
            _state.value = UpdateState.Failed(message(t), update)
        }
    }

    /** Back from the "install unknown apps" switch. */
    fun continueUpdate() {
        val s = _state.value as? UpdateState.NeedsPermission ?: return
        if (updates.canInstall()) install(s.update, s.apk)
    }

    fun allowInstalls() {
        runCatching { appContext.startActivity(updates.installPermission()) }
    }

    fun cancelUpdate() {
        job?.cancel()
        _state.value = UpdateState.Idle
    }

    fun updateLater() {
        when (val s = _state.value) {
            is UpdateState.Available -> prefs.edit().putString(KEY_SKIPPED, s.update.version).apply()
            is UpdateState.NeedsPermission -> prefs.edit().putString(KEY_SKIPPED, s.update.version).apply()
            else -> Unit
        }
        _state.value = UpdateState.Idle
    }

    fun close() {
        if (_state.value is UpdateState.Downloading) return
        _state.value = UpdateState.Idle
    }

    private fun message(t: Throwable): String = when (t) {
        is java.net.UnknownHostException, is java.net.ConnectException, is java.net.SocketTimeoutException ->
            "Couldn't reach GitHub. Check your connection and try again."
        else -> t.message ?: "Something went wrong."
    }

    companion object {
        private const val KEY_LAST_CHECK = "last_check"
        private const val KEY_SKIPPED = "skipped_version"
        private const val CHECK_EVERY_MS = 20 * 3600 * 1000L

        @Volatile
        private var instance: Updater? = null

        fun get(context: Context): Updater = instance ?: synchronized(this) {
            instance ?: Updater(context).also { instance = it }
        }
    }
}
