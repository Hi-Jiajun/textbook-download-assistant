package com.jiaocai.download.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.appPrefsDataStore by preferencesDataStore(name = "app_prefs")

/**
 * 应用级本地偏好。目前只存一件事：首次启动的使用声明是否已被用户确认。
 *
 * 说明：这里不落任何用户身份或使用行为数据，本应用也不做任何上报。
 */
class AppPrefs(private val context: Context) {

    private val agreedKey = booleanPreferencesKey("usage_notice_accepted")

    /** 用户是否已确认过首次启动的使用声明。 */
    suspend fun hasAcceptedNotice(): Boolean =
        context.appPrefsDataStore.data.first()[agreedKey] ?: false

    suspend fun setAcceptedNotice() {
        context.appPrefsDataStore.edit { it[agreedKey] = true }
    }
}
