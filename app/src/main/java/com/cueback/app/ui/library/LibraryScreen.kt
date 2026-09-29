package com.cueback.app.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cueback.app.AppContainer
import com.cueback.app.core.model.ContextCapsule
import com.cueback.app.core.model.UseCase
import com.cueback.app.data.repo.LibraryFilter
import com.cueback.app.data.repo.TimeWindow
import com.cueback.app.ui.components.AppIcon
import com.cueback.app.ui.components.Hint
import com.cueback.app.ui.components.containerViewModel
import com.cueback.app.ui.components.relativeTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModel(c: AppContainer) : ViewModel() {
    val filter = MutableStateFlow(LibraryFilter())
    val results = filter.flatMapLatest { c.contexts.search(it) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

private data class Chip(val label: String, val selected: (LibraryFilter) -> Boolean, val toggle: (LibraryFilter) -> LibraryFilter)

private val CHIPS = listOf(
    Chip("Today", { it.window == TimeWindow.TODAY }) { it.copy(window = if (it.window == TimeWindow.TODAY) TimeWindow.ALL else TimeWindow.TODAY) },
    Chip("Yesterday", { it.window == TimeWindow.YESTERDAY }) { it.copy(window = if (it.window == TimeWindow.YESTERDAY) TimeWindow.ALL else TimeWindow.YESTERDAY) },
    Chip("This week", { it.window == TimeWindow.WEEK }) { it.copy(window = if (it.window == TimeWindow.WEEK) TimeWindow.ALL else TimeWindow.WEEK) },
    Chip("Unresolved", { it.unresolvedOnly }) { it.copy(unresolvedOnly = !it.unresolvedOnly, completedOnly = false) },
    Chip("Done", { it.completedOnly }) { it.copy(completedOnly = !it.completedOnly, unresolvedOnly = false) },
    Chip("Coding", { it.useCase == UseCase.CODING }) { it.copy(useCase = if (it.useCase == UseCase.CODING) null else UseCase.CODING) },
    Chip("Study", { it.useCase == UseCase.STUDY }) { it.copy(useCase = if (it.useCase == UseCase.STUDY) null else UseCase.STUDY) },
    Chip("Writing", { it.useCase == UseCase.WRITING }) { it.copy(useCase = if (it.useCase == UseCase.WRITING) null else UseCase.WRITING) },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(onBack: () -> Unit, onContext: (String) -> Unit) {
    val vm = containerViewModel { LibraryViewModel(it) }
    val filter by vm.filter.collectAsState()
    val results by vm.results.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Library") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                OutlinedTextField(
                    value = filter.query,
                    onValueChange = { q -> vm.filter.value = filter.copy(query = q.take(100)) },
                    label = { Text("Search task, link, file, or app") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(CHIPS) { chip -> FilterChip(selected = chip.selected(filter), onClick = { vm.filter.value = chip.toggle(filter) }, label = { Text(chip.label) }) }
                }
            }
            if (results.isEmpty()) item { Hint("No contexts match. Try a different word or clear the filters.", Modifier.padding(top = 24.dp)) }
            items(results, key = { it.id }) { c -> ResultRow(c) { onContext(c.id) } }
        }
    }
}

@Composable
private fun ResultRow(c: ContextCapsule, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppIcon(c.primaryApp, Modifier.padding(top = 2.dp), size = 24)
        Column(Modifier.weight(1f)) {
            Text(c.title.text, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(c.nextAction?.let { "Next: ${it.text}" } ?: "No next step recorded", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("${c.status.name.lowercase().replaceFirstChar(Char::uppercase)}, ${relativeTime(c.pausedAt ?: c.createdAt)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
