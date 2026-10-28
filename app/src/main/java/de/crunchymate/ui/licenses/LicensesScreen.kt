package de.crunchymate.ui.licenses

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.crunchymate.R
import de.crunchymate.ui.components.EmptyState
import de.crunchymate.ui.theme.Dimens

@StringRes
private fun NoticeGroup.label(): Int =
    when (this) {
        NoticeGroup.APP -> R.string.licenses_group_app
        NoticeGroup.RUST -> R.string.licenses_group_rust
        NoticeGroup.ANDROID -> R.string.licenses_group_android
        NoticeGroup.ICONS -> R.string.licenses_group_icons
        NoticeGroup.FLAGS -> R.string.licenses_group_flags
    }

@Composable
fun LicensesDestination(
    viewModel: LicensesViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LicensesScreen(state, onBack, modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicensesScreen(
    state: LicensesUiState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Die äußere App-Hülle berücksichtigt die Systemleisten bereits
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.licenses_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_back), contentDescription = stringResource(R.string.action_back))
                    }
                },
                windowInsets = WindowInsets(0),
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                LicensesUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                LicensesUiState.Failed ->
                    EmptyState(icon = R.drawable.ic_state_error, title = stringResource(R.string.licenses_error_title))
                is LicensesUiState.Loaded -> NoticesList(state.notices)
            }
        }
    }
}

@Composable
private fun NoticesList(notices: OpenSourceNotices) {
    var expandedId by rememberSaveable { mutableStateOf<String?>(null) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = Dimens.SpaceSm)) {
        NoticeGroup.entries.forEach { group ->
            val members = notices.components.filter { it.group == group }
            if (members.isNotEmpty()) {
                item(key = "group-${group.name}") { SectionHeading(stringResource(group.label())) }
                items(members, key = { "${group.name}-${it.name}-${it.version}" }) { ComponentRow(it) }
            }
        }
        item(key = "texts-heading") { SectionHeading(stringResource(R.string.licenses_texts_heading)) }
        items(notices.licenses, key = { "license-${it.id}" }) { license ->
            LicenseTextRow(license, expanded = expandedId == license.id) {
                expandedId = if (expandedId == license.id) null else license.id
            }
        }
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.SpaceMd, vertical = Dimens.SpaceSm)
                .semantics { heading() },
    )
}

@Composable
private fun ComponentRow(component: NoticeComponent) {
    Column(Modifier.fillMaxWidth().padding(horizontal = Dimens.SpaceMd, vertical = Dimens.SpaceXs)) {
        val name =
            if (component.version.isEmpty()) {
                component.name
            } else {
                stringResource(R.string.licenses_component_line, component.name, component.version)
            }
        Text(name, style = MaterialTheme.typography.bodyMedium)
        val details =
            if (component.holder.isEmpty()) {
                component.license
            } else {
                stringResource(R.string.licenses_component_detail, component.license, component.holder)
            }
        Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LicenseTextRow(
    license: LicenseText,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = Dimens.SpaceMd, vertical = Dimens.SpaceSm),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs),
    ) {
        Text(license.id, style = MaterialTheme.typography.bodyMedium)
        if (expanded) {
            Text(license.title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(license.text, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
        }
    }
}
