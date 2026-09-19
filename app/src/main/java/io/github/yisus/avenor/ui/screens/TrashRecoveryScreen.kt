package io.github.yisus.avenor.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.yisus.avenor.TrashItem
import io.github.yisus.avenor.PlaybackViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashRecoveryScreen(viewModel: PlaybackViewModel, onBack: () -> Unit) {
    val coroutineScope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<TrashItem>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        items = viewModel.dbRepo.dao.getAllTrashItemsSync()
        loaded = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Trash Recovery") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        if (loaded) {
            if (items.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text("Trash is empty")
                }
            } else {
                LazyColumn(modifier = Modifier.padding(padding)) {
                    items(items.size) { index ->
                        val item = items[index]
                        var songTitle by remember { mutableStateOf("Unknown") }
                        LaunchedEffect(item.songId) {
                            val song = viewModel.dbRepo.dao.getSongById(item.songId.toInt())
                            songTitle = song?.title ?: "Unknown"
                        }
                        
                        ListItem(
                            headlineContent = { Text(songTitle) },
                            supportingContent = { Text("Will be deleted permanently") },
                            trailingContent = {
                                TextButton(onClick = {
                                    coroutineScope.launch {
                                        viewModel.dbRepo.dao.deleteTrashItem(item.songId)
                                        items = viewModel.dbRepo.dao.getAllTrashItemsSync()
                                    }
                                }) {
                                    Text("Recover")
                                }
                            }
                        )
                    }
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}
