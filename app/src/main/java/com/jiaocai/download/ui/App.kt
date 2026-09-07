package com.jiaocai.download.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jiaocai.download.ui.screens.DoneScreen
import com.jiaocai.download.ui.screens.DownloadScreen
import com.jiaocai.download.ui.screens.InputScreen
import com.jiaocai.download.ui.screens.LibraryScreen
import com.jiaocai.download.ui.screens.LoginScreen
import com.jiaocai.download.ui.screens.ResolveScreen
import com.jiaocai.download.ui.screens.WelcomeScreen
import com.jiaocai.download.ui.theme.TextbookTheme

@Composable
fun App(viewModel: DownloadViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    TextbookTheme {
        Surface(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                when (state.step) {
                    DownloadViewModel.Step.WELCOME -> WelcomeScreen(
                        onStart = { viewModel.go(DownloadViewModel.Step.INPUT) },
                        onOpenLibrary = viewModel::openLibrary,
                        libraryCount = state.library.size,
                    )
                    DownloadViewModel.Step.INPUT -> InputScreen(
                        urls = state.urls,
                        onUrlsChange = viewModel::setUrls,
                        onNext = { viewModel.go(DownloadViewModel.Step.LOGIN) },
                        onBack = { viewModel.go(DownloadViewModel.Step.WELCOME) },
                    )
                    DownloadViewModel.Step.LOGIN -> LoginScreen(
                        hint = state.loginHint,
                        error = state.error,
                        onToken = viewModel::onTokenCaptured,
                        onBack = { viewModel.go(DownloadViewModel.Step.INPUT) },
                    )
                    DownloadViewModel.Step.RESOLVE -> ResolveScreen(
                        resources = state.resources,
                        loading = state.loading,
                        error = state.error,
                        onDownload = viewModel::download,
                        onBack = { viewModel.go(DownloadViewModel.Step.LOGIN) },
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
                        onBack = { viewModel.go(DownloadViewModel.Step.WELCOME) },
                    )
                }
            }
        }
    }
}
