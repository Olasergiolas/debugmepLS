package com.vulnit.debugmepls.ui

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.vulnit.debugmepls.DebugConfig
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppDisplay(
    val name: String,
    val packageName: String,
    val isSelected: Boolean,
    val icon: ImageBitmap?
)

data class AppListUiState(
    val apps: List<AppDisplay> = emptyList(),
    val isLoading: Boolean = true,
    val showSystemApps: Boolean = false,
    val query: String = ""
)

class AppSelectionViewModel(application: Application) : AndroidViewModel(application) {

    private val packageManager: PackageManager = application.packageManager
    private val selectedPackages = mutableSetOf<String>()
    private var remotePrefs: SharedPreferences? = null
    private var searchJob: Job? = null
    private var allApps: List<AppDisplay> = emptyList()
    private val iconSizePx = (40 * application.resources.displayMetrics.density).toInt()

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == DebugConfig.KEY_ENABLED_PACKAGES) {
            refreshApps()
        }
    }

    private val _uiState = MutableStateFlow(AppListUiState())
    val uiState: StateFlow<AppListUiState> = _uiState

    init {
        bindXposedService()
        refreshApps()
    }

    fun onToggleApp(packageName: String, enable: Boolean) {
        val prefs = remotePrefs
        if (prefs != null) {
            val updated = prefs.getStringSet(DebugConfig.KEY_ENABLED_PACKAGES, emptySet())
                ?.toMutableSet() ?: mutableSetOf()
            if (enable) {
                updated.add(packageName)
            } else {
                updated.remove(packageName)
            }
            prefs.edit().putStringSet(DebugConfig.KEY_ENABLED_PACKAGES, updated).apply()
        }

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

    fun onQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300)
            applyFilter()
        }
    }

    private fun refreshApps() {
        viewModelScope.launch {
            val showSystemApps = _uiState.value.showSystemApps
            syncSelectionFromPrefs()
            val query = _uiState.value.query
            val apps = loadInstalledApps(showSystemApps)
            allApps = apps
            val filtered = filterApps(apps, query)
            _uiState.value = AppListUiState(
                apps = filtered,
                isLoading = false,
                showSystemApps = showSystemApps,
                query = query
            )
        }
    }

    private fun syncSelectionFromPrefs() {
        val prefs = remotePrefs ?: return
        val enabled = prefs.getStringSet(DebugConfig.KEY_ENABLED_PACKAGES, emptySet()) ?: emptySet()
        selectedPackages.clear()
        selectedPackages.addAll(enabled)
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
                    val iconBitmap = runCatching {
                        val drawable = packageManager.getApplicationIcon(appInfo)
                        drawable.toBitmap(iconSizePx, iconSizePx).asImageBitmap()
                    }.getOrNull()
                    AppDisplay(
                        name = label,
                        packageName = appInfo.packageName,
                        isSelected = selectedPackages.contains(appInfo.packageName),
                        icon = iconBitmap
                    )
                }
        }
    }

    private fun ApplicationInfo.isSystemApp(): Boolean {
        val isSystem = (flags and ApplicationInfo.FLAG_SYSTEM) != 0
        val isUpdatedSystem = (flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        return isSystem && !isUpdatedSystem
    }

    private fun bindXposedService() {
        XposedServiceHelper.registerListener(object : XposedServiceHelper.OnServiceListener {
            override fun onServiceBind(service: XposedService) {
                remotePrefs?.unregisterOnSharedPreferenceChangeListener(prefsListener)
                remotePrefs = service.getRemotePreferences(DebugConfig.PREFS_NAME)
                remotePrefs?.registerOnSharedPreferenceChangeListener(prefsListener)
                refreshApps()
            }

            override fun onServiceDied(service: XposedService) {
                remotePrefs?.unregisterOnSharedPreferenceChangeListener(prefsListener)
                remotePrefs = null
            }
        })
    }

    private fun applyFilter() {
        val query = _uiState.value.query
        val filtered = filterApps(allApps, query)
        _uiState.value = _uiState.value.copy(apps = filtered)
    }

    private fun filterApps(apps: List<AppDisplay>, query: String): List<AppDisplay> {
        val normalizedQuery = query.trim().lowercase()
        val filtered = if (normalizedQuery.isEmpty()) {
            apps
        } else {
            apps.filter {
                it.name.lowercase().contains(normalizedQuery) ||
                    it.packageName.lowercase().contains(normalizedQuery)
            }
        }
        return filtered.map { app ->
            if (app.isSelected == selectedPackages.contains(app.packageName)) {
                app
            } else {
                app.copy(isSelected = selectedPackages.contains(app.packageName))
            }
        }
    }
}
