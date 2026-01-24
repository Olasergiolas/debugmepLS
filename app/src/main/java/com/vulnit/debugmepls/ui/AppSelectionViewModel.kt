package com.vulnit.debugmepls.ui

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppDisplay(
    val name: String,
    val packageName: String,
    val isSelected: Boolean
)

data class AppListUiState(
    val apps: List<AppDisplay> = emptyList(),
    val isLoading: Boolean = true,
    val showSystemApps: Boolean = false
)

class AppSelectionViewModel(application: Application) : AndroidViewModel(application) {

    private val packageManager: PackageManager = application.packageManager
    private val selectedPackages = mutableSetOf<String>()

    private val _uiState = MutableStateFlow(AppListUiState())
    val uiState: StateFlow<AppListUiState> = _uiState

    init {
        refreshApps()
    }

    fun onToggleApp(packageName: String, enable: Boolean) {
        if (enable) {
            selectedPackages.add(packageName)
        } else {
            selectedPackages.remove(packageName)
        }
        _uiState.value = _uiState.value.copy(
            apps = _uiState.value.apps.map { app ->
                if (app.packageName == packageName) {
                    app.copy(isSelected = enable)
                } else {
                    app
                }
            }
        )
    }

    fun onToggleShowSystemApps(showSystemApps: Boolean) {
        _uiState.value = _uiState.value.copy(showSystemApps = showSystemApps, isLoading = true)
        refreshApps()
    }

    private fun refreshApps() {
        viewModelScope.launch {
            val showSystemApps = _uiState.value.showSystemApps
            val apps = loadInstalledApps(showSystemApps)
            _uiState.value = AppListUiState(
                apps = apps,
                isLoading = false,
                showSystemApps = showSystemApps
            )
        }
    }

    private suspend fun loadInstalledApps(showSystemApps: Boolean): List<AppDisplay> {
        return withContext(Dispatchers.IO) {
            val installedApplications =
                packageManager.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0L))

            installedApplications
                .filter { showSystemApps || !it.isSystemApp() }
                .sortedBy { packageManager.getApplicationLabel(it).toString().lowercase() }
                .map { appInfo ->
                    val label = packageManager.getApplicationLabel(appInfo).toString()
                    AppDisplay(
                        name = label,
                        packageName = appInfo.packageName,
                        isSelected = selectedPackages.contains(appInfo.packageName)
                    )
                }
        }
    }

    private fun ApplicationInfo.isSystemApp(): Boolean {
        val isSystem = (flags and ApplicationInfo.FLAG_SYSTEM) != 0
        val isUpdatedSystem = (flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        return isSystem && !isUpdatedSystem
    }
}
