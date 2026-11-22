package com.coolapk.wear.presentation.user

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.coolapk.wear.presentation.components.HorologistScaffold
import com.coolapk.wear.presentation.components.LoadMoreEffect
import com.coolapk.wear.presentation.components.UserRowCard
import com.coolapk.wear.presentation.components.WearCenteredLoadingOverlay
import com.coolapk.wear.presentation.components.WearEmptyState
import com.coolapk.wear.presentation.components.WearErrorRetry
import com.coolapk.wear.presentation.components.WearLoadMoreFooter
import com.coolapk.wear.presentation.components.WearScreenHeader
import com.coolapk.wear.presentation.components.rotaryEventHandler

@Composable
fun UserListScreen(
    viewModel: UserListViewModel,
    title: String,
    onUserClick: (String) -> Unit,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()

    LoadMoreEffect(
        listState = listState,
        itemCount = uiState.users.size,
        hasMore = uiState.hasMore,
        isLoading = uiState.isLoading || uiState.isLoadingMore,
        onLoadMore = { viewModel.loadMore() }
    )

    Box(modifier = Modifier.fillMaxSize()) {
        HorologistScaffold(scrollState = listState) { contentPadding ->
            TransformingLazyColumn(
                state = listState,
                contentPadding = contentPadding,
                modifier = Modifier
                    .fillMaxSize()
                    .rotaryEventHandler(listState)
            ) {
                item(key = "userlist_header") {
                    WearScreenHeader(
                        title = title,
                        subtitle = if (uiState.users.isNotEmpty()) "${uiState.users.size} 位酷友" else null,
                        transformationSpec = transformationSpec
                    )
                }

                if (uiState.error != null && uiState.users.isEmpty()) {
                    item(key = "userlist_error") {
                        WearErrorRetry(message = uiState.error.orEmpty(), onRetry = { viewModel.refresh() })
                    }
                }

                if (uiState.users.isEmpty() && !uiState.isLoading && uiState.error == null) {
                    item(key = "userlist_empty") { WearEmptyState("暂无用户记录") }
                }

                itemsIndexed(uiState.users, key = { index, user -> "${user.uid}_$index" }) { _, user ->
                    UserRowCard(
                        user = user,
                        onClick = { onUserClick(user.uid) },
                        transformation = SurfaceTransformation(transformationSpec),
                        modifier = Modifier
                            .transformedHeight(this, transformationSpec)
                            .minimumVerticalContentPadding(CardDefaults.minimumVerticalListContentPadding)
                    )
                }

                if (uiState.isLoadingMore || (!uiState.hasMore && uiState.users.isNotEmpty())) {
                    item(key = "userlist_footer") {
                        WearLoadMoreFooter(
                            isLoadingMore = uiState.isLoadingMore,
                            hasMore = uiState.hasMore,
                            hasItems = uiState.users.isNotEmpty()
                        )
                    }
                }
            }
        }

        WearCenteredLoadingOverlay(visible = uiState.isLoading && uiState.users.isEmpty())
    }
}
