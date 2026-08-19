package com.autoagent.personal.presentation.apppicker

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * AppPickerScreen
 *
 * CRASH FIX SUMMARY:
 * - All state comes from AppPickerViewModel via collectAsStateWithLifecycle()
 * - No mutableStateOf<List<*>> anywhere in this composable
 * - Search query changes go through ViewModel.onSearchQueryChanged() — never
 *   filter the list inside a composable function
 * - LazyColumn uses `key = { it.packageName }` for stable recomposition
 * - Icons are rendered with null-safety (placeholder if null)
 *
 * @param onAppSelected  Called with the selected AppInfo when user confirms selection.
 * @param onBack         Called when user presses back/cancel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPickerScreen(
    onAppSelected: (AppInfo) -> Unit,
    onBack: () -> Unit,
    viewModel: AppPickerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App Choose Karo") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {

            // ── Search bar ─────────────────────────────────────────────────────
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { query ->
                    // Route ALL state changes through the ViewModel.
                    // NEVER filter the list here in the composable.
                    viewModel.onSearchQueryChanged(query)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("App ka naam search karo...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true
            )

            // ── Error ──────────────────────────────────────────────────────────
            uiState.error?.let { error ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = error,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // ── Loading ────────────────────────────────────────────────────────
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Apps load ho rahe hain...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // ── App list ───────────────────────────────────────────────────────
            // Key facts about this LazyColumn:
            // 1. `items(...)` receives a SNAPSHOT of filteredApps — it will not
            //    change during composition because filteredApps is a val from
            //    an immutable StateFlow snapshot.
            // 2. `key = { it.packageName }` tells Compose which items are stable
            //    across recompositions, preventing flickers and index errors.
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize()
            ) {
                items(
                    items = uiState.filteredApps,
                    key = { app -> app.packageName }  // CRITICAL for stability
                ) { app ->
                    AppListItem(
                        app = app,
                        onClick = {
                            viewModel.selectApp(app)
                            onAppSelected(app)
                        }
                    )
                    HorizontalDivider(thickness = 0.5.dp)
                }

                if (!uiState.isLoading && uiState.filteredApps.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (uiState.searchQuery.isBlank())
                                    "Koi app nahi mili"
                                else
                                    "'${uiState.searchQuery}' se koi app nahi mili",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─── Individual list item ──────────────────────────────────────────────────────

@Composable
private fun AppListItem(
    app: AppInfo,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // App icon — null-safe. If icon hasn't loaded yet, show a placeholder.
        val iconDrawable = app.icon
        if (iconDrawable != null) {
            val bitmap = remember(app.packageName) {
                // toBitmap() is an extension from core-ktx, safe to call here
                // because icon is already loaded (not null)
                runCatching { iconDrawable.toBitmap(48, 48) }.getOrNull()
            }
            if (bitmap != null) {
                Image(
                    painter = BitmapPainter(bitmap.asImageBitmap()),
                    contentDescription = "${app.appName} icon",
                    modifier = Modifier.size(40.dp)
                )
            } else {
                AppIconPlaceholder()
            }
        } else {
            AppIconPlaceholder()
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.appName,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AppIconPlaceholder() {
    Surface(
        modifier = Modifier.size(40.dp),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {}
}
