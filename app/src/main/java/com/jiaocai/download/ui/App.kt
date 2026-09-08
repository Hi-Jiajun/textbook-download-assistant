package com.jiaocai.download.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jiaocai.download.ui.screens.BrowseScreen
import com.jiaocai.download.ui.screens.DoneScreen
import com.jiaocai.download.ui.screens.DownloadScreen
import com.jiaocai.download.ui.screens.LibraryScreen
import com.jiaocai.download.ui.screens.LoginScreen
import com.jiaocai.download.ui.screens.ResolveScreen
import com.jiaocai.download.ui.theme.TextbookTheme

@Composable
fun App(viewModel: DownloadViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
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
                        onAgain = viewModel::reset,
                        onOpenLibrary = viewModel::openLibrary,
                    )
                    DownloadViewModel.Step.LIBRARY -> LibraryScreen(
                        library = state.library,
                        onDelete = viewModel::deleteFromLibrary,
                        onBack = { viewModel.go(DownloadViewModel.Step.BROWSE) },
                    )
                }
            }
        }
    }
}
