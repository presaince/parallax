package com.presaince.parallax.feature.about

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.presaince.parallax.data.update.UpdateManager
import com.presaince.parallax.data.update.UpdateState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The update flow for the About screen. One state flow, the same stages the dialog renders:
 * check, download with progress, then hand the downloaded archive to the system installer.
 */
class UpdateViewModel(context: Context) : ViewModel() {

    private val manager = UpdateManager(context.applicationContext)
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** True when the system installer needs the unknown-sources permission first. */
    val needsInstallPermission: Boolean get() = !manager.canRequestInstall()

    fun check() {
        val current = _state.value
        if (current is UpdateState.Downloading || current is UpdateState.Downloaded) return
        viewModelScope.launch {
            _state.value = UpdateState.Checking
            _state.value = manager.check()
        }
    }

    fun download(force: Boolean = false) {
        val current = _state.value as? UpdateState.Available ?: return
        viewModelScope.launch {
            _state.value = UpdateState.Downloading(current.info, 0f)
            try {
                val file = manager.download(current.info, force = force) { progress ->
                    val live = _state.value
                    if (live is UpdateState.Downloading) _state.value = UpdateState.Downloading(current.info, progress)
                }
                _state.value = UpdateState.Downloaded(current.info, file)
            } catch (e: Exception) {
                _state.value = UpdateState.Failed(e.message)
            }
        }
    }

    /** The installer intent for the downloaded archive, or null while the permission is missing. */
    fun installIntent(): Intent? {
        val current = _state.value as? UpdateState.Downloaded ?: return null
        return manager.buildInstallIntent(current.file)
    }

    fun openInstallPermissionSettings() {
        manager.openInstallPermissionSettings()
    }

    fun dismiss() {
        val current = _state.value
        if (current !is UpdateState.Downloading) _state.value = UpdateState.Idle
    }
}
