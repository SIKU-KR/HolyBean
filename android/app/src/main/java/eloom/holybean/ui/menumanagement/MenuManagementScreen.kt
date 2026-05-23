package eloom.holybean.ui.menumanagement

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eloom.holybean.data.model.MenuItem
import eloom.holybean.ui.home.MenuCategories
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

// PasswordDialog.correctPassword 와 동일 (확인됨)
private const val MENU_PASSWORD = "1031"

@Composable
fun MenuManagementRoute(
    onClose: () -> Unit,
    vm: MenuManagementViewModel = hiltViewModel(),
) {
    var unlocked by remember { mutableStateOf(vm.isPasswordSessionVerified()) }
    if (!unlocked) {
        PasswordGate(
            onPass = { vm.markPasswordSessionAsVerified(); unlocked = true },
            onCancel = onClose,
        )
        return
    }

    val state by vm.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 다이얼로그 상태: null = 닫힘, editingItem == null 이면 추가 모드, 아니면 수정 모드.
    var dialogOpen by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<MenuItem?>(null) }

    LaunchedEffect(Unit) {
        vm.uiEvent.collect { event ->
            when (event) {
                is MenuManagementViewModel.UiEvent.ShowToast ->
                    android.widget.Toast.makeText(context, event.message, android.widget.Toast.LENGTH_SHORT).show()
                is MenuManagementViewModel.UiEvent.RefreshMenu -> { /* Compose 목록은 StateFlow 로 자동 갱신 */ }
            }
        }
    }

    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        // moveItem 은 현재 필터된 목록 안에서의 위치 인덱스를 받는다.
        vm.moveItem(from.index, to.index)
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "메뉴 관리",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = { editingItem = null; dialogOpen = true }) { Text("추가") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = { vm.saveMenuOrder() }) { Text("순서 저장") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = onClose) { Text("닫기") }
        }

        // 카테고리 칩(전체(0)는 정렬 의미가 없어 1~5만 노출). names 인덱스 i == 카테고리 번호.
        // ViewModel.onCategorySelected(idx) 는 내부에서 idx + 1 을 카테고리로 사용하므로 (i - 1) 을 넘긴다.
        // state.selectedCategoryIndex 에는 카테고리 번호(1..5)가 들어 있다.
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            val categoryNames = MenuCategories.names
            items((1..categoryNames.lastIndex).toList()) { i ->
                FilterChip(
                    selected = i == state.selectedCategoryIndex,
                    onClick = { vm.onCategorySelected(i - 1) },
                    label = { Text(categoryNames[i]) },
                )
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .padding(top = 10.dp),
        ) {
            items(state.filteredMenuItems, key = { it.id }) { item ->
                ReorderableItem(reorderState, key = item.id) { isDragging ->
                    val containerColor =
                        if (item.inuse) MaterialTheme.colorScheme.surface
                        else MaterialTheme.colorScheme.surfaceVariant
                    Surface(tonalElevation = if (isDragging) 4.dp else 0.dp) {
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = containerColor),
                            headlineContent = { Text(item.name) },
                            supportingContent = { Text("id:${item.id}  placement:${item.order}") },
                            leadingContent = {
                                // 드래그 핸들: 길게 눌러 정렬. 행 본문 탭은 수정.
                                IconButton(
                                    onClick = {},
                                    modifier = Modifier.longPressDraggableHandle(),
                                ) {
                                    Icon(Icons.Filled.DragHandle, contentDescription = "정렬 핸들")
                                }
                            },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("%,d".format(item.price))
                                    Spacer(Modifier.width(8.dp))
                                    Switch(
                                        checked = item.inuse,
                                        onCheckedChange = { vm.toggleMenuInUse(item) },
                                    )
                                }
                            },
                            modifier = Modifier.clickable {
                                editingItem = item
                                dialogOpen = true
                            },
                        )
                    }
                    HorizontalDivider()
                }
            }
        }
    }

    if (dialogOpen) {
        val current = editingItem
        MenuEditDialog(
            title = if (current == null) "메뉴 추가" else "메뉴 수정",
            initialName = current?.name ?: "",
            initialPrice = current?.price?.toString() ?: "",
            onConfirm = { name, price ->
                if (current == null) {
                    scope.launch {
                        val id = vm.getNextAvailableId()
                        val placement = vm.getNextAvailablePlacement()
                        vm.addMenu(id, name, price, placement)
                    }
                } else {
                    vm.updateMenu(current, name, price)
                }
                dialogOpen = false
            },
            onDismiss = { dialogOpen = false },
        )
    }
}

@Composable
private fun MenuEditDialog(
    title: String,
    initialName: String,
    initialPrice: String,
    onConfirm: (String, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var price by remember { mutableStateOf(initialPrice) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("이름") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it.filter(Char::isDigit) },
                    label = { Text("가격") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val p = price.toIntOrNull()
                if (name.isNotBlank() && p != null) onConfirm(name.trim(), p)
            }) { Text("저장") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

@Composable
private fun PasswordGate(onPass: () -> Unit, onCancel: () -> Unit) {
    var pw by remember { mutableStateOf("") }
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("비밀번호 확인") },
        text = {
            OutlinedTextField(
                value = pw,
                onValueChange = { pw = it },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                label = { Text("비밀번호를 입력하세요") },
            )
        },
        confirmButton = {
            TextButton(onClick = {
                if (pw == MENU_PASSWORD) {
                    onPass()
                } else {
                    android.widget.Toast.makeText(context, "비밀번호가 일치하지 않습니다.", android.widget.Toast.LENGTH_SHORT).show()
                }
            }) { Text("확인") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("취소") } },
    )
}
