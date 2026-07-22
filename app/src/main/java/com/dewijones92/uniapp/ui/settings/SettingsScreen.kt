package com.dewijones92.uniapp.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dewijones92.uniapp.R
import com.dewijones92.uniapp.di.AppContainer
import com.dewijones92.uniapp.settings.AppPreferences
import com.dewijones92.uniapp.ui.importexport.ImportExportScreen

/** One selectable video-quality cap for the per-network preference. */
private data class QualityOption(val label: String, val height: Int)

private val QUALITY_OPTIONS = listOf(
    QualityOption("Best", AppPreferences.UNCAPPED),
    QualityOption("2160p", 2160),
    QualityOption("1440p", 1440),
    QualityOption("1080p", 1080),
    QualityOption("720p", 720),
    QualityOption("480p", 480),
    QualityOption("360p", 360),
    QualityOption("240p", 240),
)

private fun labelFor(height: Int): String =
    QUALITY_OPTIONS.firstOrNull { it.height == height }?.label ?: "${height}p"

/**
 * App settings. Currently the per-network default video-quality caps (the
 * quality auto-picked when a video starts, so mobile data is saved). Shown as a
 * full-screen layer over the Account tab.
 */
@Composable
fun SettingsScreen(container: AppContainer, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val prefs = container.appPreferences
    val settings by prefs.settings.collectAsStateWithLifecycle()
    var showImportExport by rememberSaveable { mutableStateOf(false) }

    if (showImportExport) {
        ImportExportScreen(container, onBack = { showImportExport = false }, modifier = modifier)
        return
    }

    Surface(modifier = modifier.fillMaxSize()) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(8.dp),
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                }
                Text(
                    text = stringResource(R.string.settings),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Text(
                text = stringResource(R.string.settings_quality_section),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            QualityRow(
                label = stringResource(R.string.settings_quality_wifi),
                current = settings.wifiMaxHeight,
                onSelect = prefs::setWifiMaxHeight,
            )
            QualityRow(
                label = stringResource(R.string.settings_quality_cellular),
                current = settings.cellularMaxHeight,
                onSelect = prefs::setCellularMaxHeight,
            )
            Text(
                text = stringResource(R.string.settings_subscriptions_section),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            NavRow(
                label = stringResource(R.string.settings_import_export),
                onClick = { showImportExport = true },
            )
        }
    }
}

@Composable
private fun NavRow(label: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun QualityRow(label: String, current: Int, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        TextButton(onClick = { expanded = true }) {
            Text(labelFor(current))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            QUALITY_OPTIONS.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        onSelect(option.height)
                        expanded = false
                    },
                )
            }
        }
    }
}
