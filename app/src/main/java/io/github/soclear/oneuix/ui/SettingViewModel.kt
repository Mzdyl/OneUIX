package io.github.soclear.oneuix.ui

import android.app.Application
import android.content.pm.PackageManager
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.datastore.core.DataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import io.github.soclear.oneuix.R
import io.github.soclear.oneuix.data.Preference
import io.github.soclear.oneuix.ui.category.Category
import io.github.soclear.oneuix.ui.category.CategoryAppInfo

class SettingViewModel(application: Application) : ViewModel() {
    private val app = application

    // 始终显示的分类（即使应用未安装）
    private val alwaysShowCategories = setOf(
        Category.GalaxyStore,
        Category.Other,
        Category.Gallery,
        Category.Notes,
        Category.Calendar,
        Category.Messaging,
        Category.Browser,
        Category.Video,
        Category.Weather,
        Category.ThemeCenter,
        Category.Launcher,
        Category.DualApp,
        Category.PhotoRetouching,
        Category.HealthMonitor,
        Category.SPen
    )

    val categoryAppInfoList: StateFlow<List<CategoryAppInfo>> = flow {
        val packageManager = app.packageManager
        val categoryAppInfoList = Category.entries.mapNotNull { category ->
            // 始终显示的分类
            if (category in alwaysShowCategories) {
                val label = when (category) {
                    Category.GalaxyStore -> app.getString(R.string.galaxy_store_label)
                    Category.Other -> app.getString(R.string.other)
                    else -> try {
                        packageManager.getApplicationInfo(category.packageName, 0)
                            .loadLabel(packageManager).toString()
                    } catch (_: Exception) {
                        category.name
                    }
                }
                val icon = try {
                    packageManager.getApplicationInfo(category.packageName, 0)
                        .loadIcon(packageManager).toBitmap().asImageBitmap()
                } catch (_: Exception) {
                    app.getDrawable(R.drawable.ic_launcher_foreground)!!
                        .toBitmap().asImageBitmap()
                }
                return@mapNotNull CategoryAppInfo(category, label, icon)
            }

            val applicationInfo = try {
                packageManager.getApplicationInfo(category.packageName, 0)
            } catch (_: PackageManager.NameNotFoundException) {
                return@mapNotNull null
            }
            val label = applicationInfo.loadLabel(packageManager).toString()
            val icon = applicationInfo.loadIcon(packageManager).toBitmap().asImageBitmap()
            CategoryAppInfo(category, label, icon)
        }
        emit(categoryAppInfoList)
    }.flowOn(Dispatchers.IO).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val dataStore: DataStore<Preference> = application.dataStore

    val preference = dataStore.data.stateIn(
        scope = viewModelScope, started = SharingStarted.Eagerly, initialValue = Preference()
    )

    fun updateData(nextPreference: (currentPreference: Preference) -> Preference) {
        viewModelScope.launch {
            dataStore.updateData {
                nextPreference(it)
            }
        }
    }
}
