package com.jiaocai.download.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jiaocai.download.ui.screens.AboutScreen
import com.jiaocai.download.ui.screens.BrowseScreen
import com.jiaocai.download.ui.screens.DoneScreen
import com.jiaocai.download.ui.screens.DownloadScreen
import com.jiaocai.download.ui.screens.LibraryScreen
import com.jiaocai.download.ui.screens.LoginScreen
import com.jiaocai.download.ui.screens.ResolveScreen
import com.jiaocai.download.ui.screens.UsageNoticeDialog
import com.jiaocai.download.ui.theme.TextbookTheme

@Composable
fun App(viewModel: DownloadViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()

    // 系统返回键与界面上的返回箭头保持一致，避免在子页面按返回直接退出应用。
    // 例外：首次启动声明未确认时返回键不生效（必须主动点「我已知悉」）；
    // 下载进行中也不响应返回，避免留下半截状态。
    BackHandler(enabled = state.needAgreement || state.step != DownloadViewModel.Step.BROWSE) {
        when {
            state.needAgreement -> Unit
            state.step == DownloadViewModel.Step.DONE -> viewModel.reset()
            state.step == DownloadViewModel.Step.DOWNLOAD -> Unit
            else -> viewModel.go(DownloadViewModel.Step.BROWSE)
        }
    }

    TextbookTheme {
        Surface(Modifier.fillMaxSize()) {
            // 安全区适配在各界面内按需处理（内容页避开状态栏/挖孔；下载页全屏沉浸）
            Box(Modifier.fillMaxSize()) {
                when (state.step) {
                    DownloadViewModel.Step.BROWSE -> BrowseScreen(
                        viewModel = viewModel,
                        state = state,
                        onOpenLibrary = viewModel::openLibrary,
                        onOpenCredentials = viewModel::openCredentials,
                        onOpenAbout = viewModel::openAbout,
                    )
                    DownloadViewModel.Step.LOGIN -> LoginScreen(
                        hint = state.loginHint,
                        error = state.error,
                        loggedIn = state.loggedIn,
                        onToken = viewModel::onTokenCaptured,
                        onLogout = viewModel::logout,
                        onBack = { viewModel.go(DownloadViewModel.Step.BROWSE) },
                    )
                    DownloadViewModel.Step.RESOLVE -> ResolveScreen(
                        resources = state.resources,
                        loading = state.loading,
                        error = state.error,
                        onDownload = viewModel::download,
                        onBack = { viewModel.go(DownloadViewModel.Step.BROWSE) },
                    )
                    DownloadViewModel.Step.DOWNLOAD -> DownloadScreen(
                        done = state.progressDone,
                        total = state.progressTotal,
                        error = state.error,
                    )
                    DownloadViewModel.Step.DONE -> DoneScreen(
                        downloadedCount = state.downloadedCount,
                        bookmarksCount = state.bookmarksCount,
                        onBackHome = viewModel::reset,
                        onOpenLibrary = viewModel::openLibrary,
                    )
                    DownloadViewModel.Step.LIBRARY -> LibraryScreen(
                        library = state.library,
                        onDelete = viewModel::deleteFromLibrary,
                        onBack = { viewModel.go(DownloadViewModel.Step.BROWSE) },
                    )
                    DownloadViewModel.Step.ABOUT -> AboutScreen(
                        onBack = { viewModel.go(DownloadViewModel.Step.BROWSE) },
                    )
                }

                // 首次启动必须主动确认使用声明后才能使用（不允许点外部或按返回键跳过）。
                if (state.needAgreement) {
                    UsageNoticeDialog(onAccept = viewModel::acceptNotice)
                }
            }
        }
    }
}
