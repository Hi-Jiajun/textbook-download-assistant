package com.jiaocai.download.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.credentialsDataStore by preferencesDataStore(name = "credentials")

/** 本地持久化登录凭据（只存本机，不上传）。 */
class TokenStore(private val context: Context) {

    private val key = stringPreferencesKey("token_json")

    /** 返回原始三项 JSON；未保存时为 null。 */
    val tokenJson: Flow<String?> = context.credentialsDataStore.data.map { it[key] }

    suspend fun save(tokenJson: String) {
        context.credentialsDataStore.edit { it[key] = tokenJson }
    }

    suspend fun clear() {
        context.credentialsDataStore.edit { it.remove(key) }
    }
}
