package com.vulnit.debugmepls

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vulnit.debugmepls.ui.AppDisplay
import com.vulnit.debugmepls.ui.AppListUiState
import com.vulnit.debugmepls.ui.AppSelectionViewModel
import com.vulnit.debugmepls.ui.theme.DebugmepLSTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DebugmepLSTheme {
                AppSelectionRoute()
            }
        }
    }
}

@Composable
fun AppSelectionRoute(viewModel: AppSelectionViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    AppSelectionScreen(
        uiState = uiState,
        onToggleApp = viewModel::onToggleApp,
        onToggleShowSystemApps = viewModel::onToggleShowSystemApps,
        onQueryChange = viewModel::onQueryChange
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSelectionScreen(
    uiState: AppListUiState,
    onToggleApp: (String, Boolean) -> Unit,
    onToggleShowSystemApps: (Boolean) -> Unit,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by rememberSaveable { mutableStateOf(false) }
    var searchActive by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    if (searchActive) {
                        InlineSearchField(
                            query = uiState.query,
                            onQueryChange = onQueryChange,
                            onClose = {
                                searchActive = false
                                onQueryChange("")
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text(
                            text = buildAnnotatedString {
                                append("debugmep")
                                withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) {
                                    append("LS")
                                }
                                append(" 🙏")
                            },
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { searchActive = true }) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = "Search"
                        )
                    }
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Filled.Menu,
                            contentDescription = "Open settings"
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Show system apps",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Switch(
                                checked = uiState.showSystemApps,
                                onCheckedChange = onToggleShowSystemApps,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                                    uncheckedBorderColor = MaterialTheme.colorScheme.outline
                                )
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    StatusCard(
                        isConnected = uiState.isServiceConnected,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 0.dp)
                    )
                    if (uiState.apps.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "No applications found.")
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(top = 4.dp, bottom = 8.dp)
                        ) {
                            items(
                                items = uiState.apps,
                                key = { it.packageName }
                            ) { app ->
                                AppRow(
                                    app = app,
                                    onToggle = { checked -> onToggleApp(app.packageName, checked) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatusCard(
    isConnected: Boolean,
    modifier: Modifier = Modifier
) {
    val statusColor =
        if (isConnected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.tertiary
    val containerColor =
        if (isConnected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.tertiaryContainer
    val borderColor =
        if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
    val text = if (isConnected) "LSPosed connected" else "Not connected to LSPosed service"

    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Status:",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = text,
                color = statusColor,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InlineSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium,
        placeholder = {
            Text(
                text = "Search apps",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null
            )
        },
        trailingIcon = {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Close search",
                modifier = Modifier.clickable { onClose() }
            )
        },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
            unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
            disabledContainerColor = androidx.compose.ui.graphics.Color.Transparent,
            focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
            unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
            disabledIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
            cursorColor = MaterialTheme.colorScheme.primary,
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
            focusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            focusedTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unfocusedTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
    )
}

@Composable
fun AppRow(
    app: AppDisplay,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .fillMaxWidth()
            .clickable { onToggle(!app.isSelected) },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        val itemContainerColor = if (app.isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        }
        val itemContentColor = if (app.isSelected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        }
        ListItem(
            leadingContent = {
                if (app.icon != null) {
                    Image(
                        painter = BitmapPainter(app.icon),
                        contentDescription = null,
                        modifier = Modifier.size(36.dp)
                    )
                } else {
                    Spacer(modifier = Modifier.size(36.dp))
                }
            },
            headlineContent = {
                Text(
                    text = app.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = itemContentColor
                )
            },
            supportingContent = {
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = itemContentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            trailingContent = {
                val switchColors = if (app.isSelected) {
                    SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        uncheckedThumbColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        uncheckedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                        uncheckedBorderColor = MaterialTheme.colorScheme.primary
                    )
                } else {
                    SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                        checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                        uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                        uncheckedBorderColor = MaterialTheme.colorScheme.outline
                    )
                }
                Switch(
                    checked = app.isSelected,
                    onCheckedChange = onToggle,
                    colors = switchColors
                )
            },
            colors = androidx.compose.material3.ListItemDefaults.colors(
                containerColor = itemContainerColor,
                headlineColor = itemContentColor,
                supportingColor = itemContentColor,
                leadingIconColor = itemContentColor,
                trailingIconColor = itemContentColor
            )
        )
    }
}
