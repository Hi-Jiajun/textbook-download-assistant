package com.jiaocai.download.ui

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jiaocai.download.data.AppPrefs
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
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DownloadViewModel(app: Application) : AndroidViewModel(app) {

    enum class Step { BROWSE, LOGIN, RESOLVE, DOWNLOAD, DONE, LIBRARY, ABOUT }

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
        val filterStages: List<String> = emptyList(),
        val filterSubjects: List<String> = emptyList(),
        val filterVersions: List<String> = emptyList(),
        val loggedIn: Boolean = false,
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
        // 首次启动的使用声明是否待用户确认
        val needAgreement: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    private val tokenStore = TokenStore(app)
    private val engine = DownloadEngine(app)
    private val libraryStore = LibraryStore(app)
    private val appPrefs = AppPrefs(app)
    private var credentials: AuthSigner.Credentials? = null
    private var pendingDownload = false

    init {
        _state.value = _state.value.copy(library = libraryStore.load())
        loadCatalog()
        restoreCredentials()
        checkUsageNotice()
    }

    /** 首次启动时确认使用声明是否已被接受；读取失败时不阻塞用户。 */
    private fun checkUsageNotice() {
        viewModelScope.launch {
            val accepted = runCatching { appPrefs.hasAcceptedNotice() }.getOrDefault(true)
            if (!accepted) _state.value = _state.value.copy(needAgreement = true)
        }
    }

    /** 用户点了「我已知悉」。 */
    fun acceptNotice() {
        _state.value = _state.value.copy(needAgreement = false)
        viewModelScope.launch { runCatching { appPrefs.setAcceptedNotice() } }
    }

    /** 启动时从本地恢复上次登录的凭据，实现「记住登录」。 */
    private fun restoreCredentials() {
        viewModelScope.launch {
            val saved = tokenStore.load()
            if (saved != null && credentials == null) {
                credentials = try {
                    AuthSigner.parseTokenInput(saved)
                } catch (_: Exception) {
                    null
                }
                _state.value = _state.value.copy(loggedIn = credentials != null)
            }
        }
    }

    private fun loadCatalog(forceRefresh: Boolean = false) {
        if (_state.value.catalogLoading) return
        _state.value = _state.value.copy(catalogLoading = true, catalogError = null)
        viewModelScope.launch {
            try {
                val books = CatalogApi.fetchTextbooks(getApplication(), forceRefresh)
                val (stages, subjects, versions) = withContext(Dispatchers.Default) {
                    Triple(
                        books.map { it.stage }.filter { it.isNotBlank() }.distinct().sorted(),
                        books.map { it.subject }.filter { it.isNotBlank() }.distinct().sorted(),
                        books.map { it.version }.filter { it.isNotBlank() }.distinct().sorted(),
                    )
                }
                _state.value = _state.value.copy(
                    textbooks = books,
                    filterStages = stages,
                    filterSubjects = subjects,
                    filterVersions = versions,
                    catalogLoading = false,
                )
                prefetchCoverThumbnails(books)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    catalogLoading = false,
                    catalogError = e.message ?: "加载教材目录失败，请检查网络后重试。",
                )
            }
        }
    }

    /** 预取首屏前几张封面，缓解第一次快速滑动的卡顿；失败不影响主流程。 */
    private fun prefetchCoverThumbnails(books: List<Textbook>) {
        val loader = getApplication<Application>().imageLoader
        books.asSequence()
            .mapNotNull { it.thumb?.takeIf(String::isNotBlank) }
            .distinct()
            .take(12)
            .forEach { url ->
                runCatching {
                    loader.enqueue(
                        ImageRequest.Builder(getApplication())
                            .data(url)
                            .size(256)
                            .build(),
                    )
                }
            }
    }

    fun refreshCatalog() = loadCatalog(forceRefresh = true)

    fun setQuery(value: String) { _state.value = _state.value.copy(query = value) }
    fun setStageFilter(value: String) {
        _state.value = _state.value.copy(stageFilter = value, subjectFilter = "", versionFilter = "")
    }
    fun setSubjectFilter(value: String) {
        _state.value = _state.value.copy(subjectFilter = value, versionFilter = "")
    }
    fun setVersionFilter(value: String) { _state.value = _state.value.copy(versionFilter = value) }

    fun toggleSelect(id: String) {
        val cur = _state.value.selectedIds
        _state.value = _state.value.copy(selectedIds = if (id in cur) cur - id else cur + id)
    }

    fun go(step: Step) {
        _state.value = _state.value.copy(step = step, error = null)
    }

    /** 打开「关于与免责」页。 */
    fun openAbout() {
        _state.value = _state.value.copy(step = Step.ABOUT, error = null)
    }

    fun openLibrary() {
        _state.value = _state.value.copy(step = Step.LIBRARY, library = libraryStore.load(), error = null)
    }

    /** 从首页进入「登录/凭据」页，可重新登录或手动更新凭据。 */
    fun openCredentials() {
        pendingDownload = false
        _state.value = _state.value.copy(
            step = Step.LOGIN,
            error = null,
            loginHint = if (credentials != null) "当前已是登录状态，可重新登录或手动更新凭据。" else "请登录国家中小学智慧教育平台账号，或手动粘贴凭据。",
        )
    }

    /** 退出登录：清除本地保存的凭据并重置登录状态。 */
    fun logout() {
        credentials = null
        pendingDownload = false
        viewModelScope.launch { tokenStore.clear() }
        _state.value = _state.value.copy(
            loggedIn = false,
            loginHint = "已退出登录。可重新登录或手动粘贴凭据。",
            error = null,
        )
    }

    /** 浏览页点击「去下载」：确保已登录后开始解析并下载所选教材。 */
    fun startDownload() {
        val selected = _state.value.textbooks.filter { it.id in _state.value.selectedIds }
        if (selected.isEmpty()) {
            _state.value = _state.value.copy(error = "请先勾选至少一本教材。")
            return
        }
        // 合规约束：限制单次批量规模，见文件末尾 MAX_BATCH_SIZE 说明。
        if (selected.size > MAX_BATCH_SIZE) {
            _state.value = _state.value.copy(
                error = "单次最多下载 $MAX_BATCH_SIZE 本，请分几次下载（当前已勾选 ${selected.size} 本）。",
            )
            return
        }
        if (credentials == null) {
            pendingDownload = true
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
                val wasLoggedIn = credentials != null
                credentials = cred
                tokenStore.save(json)
                if (pendingDownload) {
                    val selected = _state.value.textbooks.filter { it.id in _state.value.selectedIds }
                    _state.value = _state.value.copy(loggedIn = true, error = null)
                    beginResolveDownload(selected)
                } else if (!wasLoggedIn) {
                    // 全新登录：提示成功并直接返回首页，避免停留在登录页或看到官网首页。
                    Toast.makeText(getApplication(), "登录成功", Toast.LENGTH_SHORT).show()
                    _state.value = _state.value.copy(
                        loggedIn = true,
                        step = Step.BROWSE,
                        loginHint = "登录成功，凭据已保存。",
                        error = null,
                    )
                } else {
                    // 已登录状态下打开登录页（WebView 自动识别到存量 token）：留在登录页显示已登录。
                    _state.value = _state.value.copy(
                        loggedIn = true,
                        loginHint = "凭据已保存，当前已登录。",
                        error = null,
                    )
                }
            } catch (e: AuthSigner.TokenInputError) {
                _state.value = _state.value.copy(error = "未能识别登录凭据：${e.message}")
            }
        }
    }

    private fun beginResolveDownload(selected: List<Textbook>) {
        pendingDownload = false
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
                // 合规约束：逐本之间留出固定间隔，避免形成高频批量抓取。
                if (index > 0) delay(DOWNLOAD_INTERVAL_MS)
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
        if (libraryStore.remove(path)) {
            _state.value = _state.value.copy(library = libraryStore.load(), error = null)
            Toast.makeText(getApplication(), "已删除", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(getApplication(), "删除失败：文件可能被其他应用占用", Toast.LENGTH_SHORT).show()
        }
    }

    /** 下载完成后返回首页：清掉本次下载流程状态，但保留目录与登录态，避免每次都要重新登录。 */
    fun reset() {
        pendingDownload = false
        val s = _state.value
        _state.value = s.copy(
            step = Step.BROWSE,
            selectedIds = emptySet(),
            resources = emptyList(),
            loading = false,
            error = null,
            progressDone = 0,
            progressTotal = 0,
            currentIndex = 0,
            downloadedCount = 0,
            bookmarksCount = 0,
            loginHint = if (credentials != null) "当前已登录。" else "请登录...",
        )
    }

    companion object {
        /**
         * 合规约束：单次批量下载上限，以及逐本之间的固定间隔。
         *
         * 本应用定位是「个人学习使用」的工具，不是批量抓取器。放开这两个限制会让
         * 使用形态向「批量获取平台内容」偏移：既违反平台《用户协议》第 6.1 条对
         * 自动化程序获取平台内容的禁止，也会加重工具作者一方的风险。
         *
         * 修改前请先阅读 README 的「免责声明」与「本项目的红线」两节。
         */
        const val MAX_BATCH_SIZE = 10
        const val DOWNLOAD_INTERVAL_MS = 2000L
    }
}
