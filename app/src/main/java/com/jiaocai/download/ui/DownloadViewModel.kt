package com.jiaocai.download.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jiaocai.download.data.AuthSigner
import com.jiaocai.download.data.CatalogApi
import com.jiaocai.download.data.DownloadEngine
import com.jiaocai.download.data.LibraryStore
import com.jiaocai.download.data.PdfBookmarker
import com.jiaocai.download.data.SmartEduApi
import com.jiaocai.download.data.TokenStore
import com.jiaocai.download.model.ResourceInfo
import com.jiaocai.download.model.SavedItem
import com.jiaocai.download.model.Textbook
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DownloadViewModel(app: Application) : AndroidViewModel(app) {

    enum class Step { BROWSE, LOGIN, RESOLVE, DOWNLOAD, DONE, LIBRARY }

    data class UiState(
        val step: Step = Step.BROWSE,
        // 教材目录与筛选
        val textbooks: List<Textbook> = emptyList(),
        val catalogLoading: Boolean = false,
        val catalogError: String? = null,
        val query: String = "",
        val stageFilter: String = "",
        val subjectFilter: String = "",
        val versionFilter: String = "",
        val selectedIds: Set<String> = emptySet(),
        // 登录与下载
        val loginHint: String = "请在下方网页中先登录国家中小学智慧教育平台账号，登录成功后会在这里提示。",
        val resources: List<ResourceInfo> = emptyList(),
        val loading: Boolean = false,
        val error: String? = null,
        val progressDone: Long = 0,
        val progressTotal: Long = 0,
        val currentIndex: Int = 0,
        val downloadedCount: Int = 0,
        val bookmarksCount: Int = 0,
        val library: List<SavedItem> = emptyList(),
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    private val tokenStore = TokenStore(app)
    private val engine = DownloadEngine(app)
    private val libraryStore = LibraryStore(app)
    private var credentials: AuthSigner.Credentials? = null

    init {
        _state.value = _state.value.copy(library = libraryStore.load())
        loadCatalog()
    }

    private fun loadCatalog() {
        if (_state.value.catalogLoading) return
        _state.value = _state.value.copy(catalogLoading = true, catalogError = null)
        viewModelScope.launch {
            try {
                val books = CatalogApi.fetchTextbooks()
                _state.value = _state.value.copy(textbooks = books, catalogLoading = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    catalogLoading = false,
                    catalogError = e.message ?: "加载教材目录失败，请检查网络后重试。",
                )
            }
        }
    }

    fun refreshCatalog() = loadCatalog()

    fun setQuery(value: String) { _state.value = _state.value.copy(query = value) }
    fun setStageFilter(value: String) { _state.value = _state.value.copy(stageFilter = value) }
    fun setSubjectFilter(value: String) { _state.value = _state.value.copy(subjectFilter = value) }
    fun setVersionFilter(value: String) { _state.value = _state.value.copy(versionFilter = value) }

    fun toggleSelect(id: String) {
        val cur = _state.value.selectedIds
        _state.value = _state.value.copy(selectedIds = if (id in cur) cur - id else cur + id)
    }

    fun go(step: Step) {
        _state.value = _state.value.copy(step = step, error = null)
    }

    fun openLibrary() {
        _state.value = _state.value.copy(step = Step.LIBRARY, library = libraryStore.load(), error = null)
    }

    /** 浏览页点击「去下载」：确保已登录后开始解析并下载所选教材。 */
    fun startDownload() {
        val selected = _state.value.textbooks.filter { it.id in _state.value.selectedIds }
        if (selected.isEmpty()) {
            _state.value = _state.value.copy(error = "请先勾选至少一本教材。")
            return
        }
        if (credentials == null) {
            _state.value = _state.value.copy(
                step = Step.LOGIN,
                loginHint = "请先登录国家中小学智慧教育平台账号，登录成功后会自动解析并下载你选中的教材。",
                error = null,
            )
            return
        }
        beginResolveDownload(selected)
    }

    /** WebView 捕获到登录凭据 JSON 后调用：保存本地并继续解析下载所选教材。 */
    fun onTokenCaptured(json: String) {
        viewModelScope.launch {
            try {
                val cred = AuthSigner.parseTokenInput(json)
                credentials = cred
                tokenStore.save(json)
                val selected = _state.value.textbooks.filter { it.id in _state.value.selectedIds }
                _state.value = _state.value.copy(error = null)
                beginResolveDownload(selected)
            } catch (e: AuthSigner.TokenInputError) {
                _state.value = _state.value.copy(error = "未能识别登录凭据：${e.message}")
            }
        }
    }

    private fun beginResolveDownload(selected: List<Textbook>) {
        val cred = credentials
        if (selected.isEmpty() || cred == null) {
            _state.value = _state.value.copy(error = "尚未获取登录凭据，请回到登录步骤。", step = Step.LOGIN)
            return
        }
        _state.value = _state.value.copy(loading = true, error = null, step = Step.RESOLVE)
        viewModelScope.launch {
            val results = mutableListOf<ResourceInfo>()
            try {
                for (b in selected) {
                    results.add(SmartEduApi.resolveById(b.id, cred, bookmarks = true))
                }
                _state.value = _state.value.copy(resources = results, loading = false, step = Step.RESOLVE)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    loading = false,
                    error = e.message ?: "解析失败，请检查链接与网络。",
                )
            }
        }
    }

    fun download() {
        val resources = _state.value.resources
        if (resources.isEmpty()) return
        val cred = credentials ?: run {
            _state.value = _state.value.copy(error = "登录凭据缺失。")
            return
        }
        _state.value = _state.value.copy(loading = true, error = null, step = Step.DOWNLOAD)
        viewModelScope.launch {
            val saved = mutableListOf<SavedItem>()
            var bookmarks = 0
            resources.forEachIndexed { index, resource ->
                _state.value = _state.value.copy(currentIndex = index)
                try {
                    val file = engine.download(resource, cred) { done, total ->
                        _state.value = _state.value.copy(progressDone = done, progressTotal = total)
                    }
                    val added = PdfBookmarker.addBookmarks(file, resource.chapters)
                    if (added) bookmarks++
                    val item = SavedItem(resource.title, file.absolutePath, resource.format, resource.edition, System.currentTimeMillis())
                    libraryStore.append(item)
                    saved.add(item)
                    _state.value = _state.value.copy(downloadedCount = saved.size, bookmarksCount = bookmarks)
                } catch (e: Exception) {
                    _state.value = _state.value.copy(
                        loading = false,
                        error = "第 ${index + 1}/${resources.size} 本下载失败：${e.message}",
                    )
                    return@launch
                }
            }
            _state.value = _state.value.copy(
                loading = false,
                library = libraryStore.load(),
                step = Step.DONE,
            )
        }
    }

    fun deleteFromLibrary(path: String) {
        _state.value = _state.value.copy(library = libraryStore.remove(path))
    }

    fun reset() {
        credentials = null
        _state.value = UiState(library = libraryStore.load())
    }
}
