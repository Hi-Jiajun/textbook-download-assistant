package com.jiaocai.download.ui

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jiaocai.download.data.AppPrefs
import com.jiaocai.download.data.AuthExpiredException
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
import kotlinx.coroutines.Job
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

    /** 当前在跑的解析任务。用户离开该步骤时取消，避免「返回后又被拽回解析页」。 */
    private var flowJob: Job? = null

    /** 用户主动导航时自增，用来判断异步结果是否已经过期。 */
    private var flowEpoch = 0

    /** 下载失败后的续传点：失败的那一本的序号，以及此前已完成的计数。 */
    private var retryFromIndex = 0
    private var retrySaved = 0
    private var retryBookmarks = 0

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
        if (id in cur) {
            _state.value = _state.value.copy(selectedIds = cur - id, error = null)
            return
        }
        // 选满即止：之前是「允许勾选、点下载时才发现超限且不提示」，用户只会觉得点了没反应。
        if (cur.size >= MAX_BATCH_SIZE) {
            _state.value = _state.value.copy(
                error = "单次最多下载 $MAX_BATCH_SIZE 本（合规限制）。请先下载完这批，再勾选下一批。",
            )
            return
        }
        _state.value = _state.value.copy(selectedIds = cur + id, error = null)
    }

    /** 用户主动关掉错误提示。 */
    fun dismissError() {
        _state.value = _state.value.copy(error = null)
    }

    /** 取消正在跑的解析任务，并让它的结果作废。 */
    private fun cancelFlow() {
        flowEpoch++
        flowJob?.cancel()
        flowJob = null
    }

    fun go(step: Step) {
        if (step != Step.DOWNLOAD) cancelFlow()
        _state.value = _state.value.copy(step = step, error = null)
    }

    /** 打开「关于与免责」页。 */
    fun openAbout() {
        cancelFlow()
        _state.value = _state.value.copy(step = Step.ABOUT, error = null)
    }

    fun openLibrary() {
        cancelFlow()
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
        cancelFlow()
        val epoch = flowEpoch
        _state.value = _state.value.copy(loading = true, error = null, step = Step.RESOLVE, resources = emptyList())
        flowJob = viewModelScope.launch {
            val results = mutableListOf<ResourceInfo>()
            try {
                for (b in selected) {
                    results.add(SmartEduApi.resolveById(b.id, cred, bookmarks = true))
                }
                // 用户可能已经按返回离开解析页：这时结果必须作废，否则会把界面拽回来。
                if (epoch != flowEpoch) return@launch
                _state.value = _state.value.copy(resources = results, loading = false, step = Step.RESOLVE, error = null)
            } catch (e: Exception) {
                if (epoch != flowEpoch) return@launch
                handleFlowError(e, "解析失败，请检查链接与网络。")
            }
        }
    }

    /**
     * 解析/下载失败的统一处理：凭据失效时顺手清掉本地凭据并把界面切回未登录，
     * 免得用户对着「已登录」一直重试。
     */
    private fun handleFlowError(e: Exception, fallback: String) {
        val expired = e is AuthExpiredException
        if (expired) {
            credentials = null
            pendingDownload = false
            viewModelScope.launch { runCatching { tokenStore.clear() } }
        }
        _state.value = _state.value.copy(
            loading = false,
            loggedIn = if (expired) false else _state.value.loggedIn,
            loginHint = if (expired) "登录状态已失效，请重新登录。" else _state.value.loginHint,
            error = e.message ?: fallback,
        )
    }

    /** 从头下载（解析页点「开始下载」）。 */
    fun download() = downloadFrom(0, 0, 0)

    /** 下载失败后点「重试」：从失败的那一本继续，已经下载好的不重复下载。 */
    fun retryDownload() = downloadFrom(retryFromIndex, retrySaved, retryBookmarks)

    private fun downloadFrom(startIndex: Int, savedBefore: Int, bookmarksBefore: Int) {
        val resources = _state.value.resources
        if (resources.isEmpty() || startIndex !in resources.indices) return
        val cred = credentials ?: run {
            _state.value = _state.value.copy(error = "登录凭据缺失，请先登录。")
            return
        }
        cancelFlow()
        val epoch = flowEpoch
        _state.value = _state.value.copy(
            loading = true,
            error = null,
            step = Step.DOWNLOAD,
            progressDone = 0,
            progressTotal = 0,
            downloadedCount = savedBefore,
            bookmarksCount = bookmarksBefore,
        )
        flowJob = viewModelScope.launch {
            var saved = savedBefore
            var bookmarks = bookmarksBefore
            for (index in startIndex until resources.size) {
                _state.value = _state.value.copy(currentIndex = index, progressDone = 0, progressTotal = 0)
                // 合规约束：逐本之间留出固定间隔，避免形成高频批量抓取。
                if (index > startIndex) delay(DOWNLOAD_INTERVAL_MS)
                try {
                    val resource = resources[index]
                    val file = engine.download(resource, cred) { done, total ->
                        _state.value = _state.value.copy(progressDone = done, progressTotal = total)
                    }
                    val added = PdfBookmarker.addBookmarks(file, resource.chapters)
                    if (added) bookmarks++
                    val item = SavedItem(resource.title, file.absolutePath, resource.format, resource.edition, System.currentTimeMillis())
                    libraryStore.append(item)
                    saved++
                    _state.value = _state.value.copy(downloadedCount = saved, bookmarksCount = bookmarks)
                } catch (e: Exception) {
                    if (epoch != flowEpoch) return@launch
                    // 记住续传点：下次「重试」直接从这一本开始。
                    retryFromIndex = index
                    retrySaved = saved
                    retryBookmarks = bookmarks
                    val prefix = if (resources.size > 1) "第 ${index + 1}/${resources.size} 本下载失败：" else ""
                    handleFlowError(e, "下载失败，请检查网络后重试。")
                    _state.value = _state.value.copy(error = prefix + (_state.value.error ?: "下载失败"))
                    return@launch
                }
            }
            retryFromIndex = 0
            retrySaved = 0
            retryBookmarks = 0
            _state.value = _state.value.copy(
                loading = false,
                library = libraryStore.load(),
                step = Step.DONE,
                downloadedCount = saved,
                bookmarksCount = bookmarks,
            )
        }
    }

    /** 下载失败后从「下载中」页面退回首页：保留已勾选的教材，方便再次发起。 */
    fun leaveFailedDownload() {
        cancelFlow()
        _state.value = _state.value.copy(
            step = Step.BROWSE,
            loading = false,
            error = null,
            progressDone = 0,
            progressTotal = 0,
        )
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
        cancelFlow()
        pendingDownload = false
        retryFromIndex = 0
        retrySaved = 0
        retryBookmarks = 0
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
