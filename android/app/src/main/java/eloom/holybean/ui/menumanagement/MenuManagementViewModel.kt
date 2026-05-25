package eloom.holybean.ui.menumanagement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import eloom.holybean.data.model.MenuItem
import eloom.holybean.data.repository.MenuRepository
import eloom.holybean.util.launchSafely
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class MenuManagementViewModel @Inject constructor(
    private val menuRepository: MenuRepository,
) : ViewModel() {

    data class UiState(
        val allMenuItems: List<MenuItem> = emptyList(),
        val filteredMenuItems: List<MenuItem> = emptyList(),
        val selectedCategoryIndex: Int = 1, // Default to "ICE커피"
        val isLoading: Boolean = false
    )

    sealed class UiEvent {
        data class ShowToast(val message: String) : UiEvent()
        object RefreshMenu : UiEvent()
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _uiEvent = MutableSharedFlow<UiEvent>(
        replay = 0,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val uiEvent: SharedFlow<UiEvent> = _uiEvent.asSharedFlow()

    private var passwordSessionVerified = false

    fun isPasswordSessionVerified(): Boolean = passwordSessionVerified

    fun markPasswordSessionAsVerified() {
        passwordSessionVerified = true
    }

    init {
        loadMenuList()
    }

    private fun loadMenuList() {
        viewModelScope.launchSafely(onError = { e ->
            _uiState.update { it.copy(isLoading = false) }
            _uiEvent.tryEmit(UiEvent.ShowToast("Error loading menu: ${e.message}"))
        }) {
            _uiState.update { it.copy(isLoading = true) }
            menuRepository.getMenuList()
                .map { list -> list.sortedBy { it.order } }
                .collect { menuList ->
                    _uiState.update { it.copy(allMenuItems = menuList, isLoading = false) }
                    filterMenuByCategory(_uiState.value.selectedCategoryIndex)
                }
        }
    }

    fun onCategorySelected(index: Int) {
        val category = index + 1
        _uiState.update { it.copy(selectedCategoryIndex = category) }
        filterMenuByCategory(category)
    }

    private fun filterMenuByCategory(category: Int) {
        val filtered = _uiState.value.allMenuItems.filter { it.id / 1000 == category }
        _uiState.update { it.copy(filteredMenuItems = filtered) }
    }

    fun moveItem(fromPosition: Int, toPosition: Int) {
        val category = _uiState.value.selectedCategoryIndex
        val currentAllItems = _uiState.value.allMenuItems.toMutableList()

        val categoryItems = currentAllItems.filter { it.id / 1000 == category }.toMutableList()

        if (fromPosition < 0 || fromPosition >= categoryItems.size || toPosition < 0 || toPosition >= categoryItems.size) {
            return
        }

        val movedItem = categoryItems.removeAt(fromPosition)
        categoryItems.add(toPosition, movedItem)

        categoryItems.forEachIndexed { index, menuItem ->
            menuItem.order = (category * 1000) + (index + 1)
        }

        val otherItems = currentAllItems.filter { it.id / 1000 != category }
        val newAllItems = (otherItems + categoryItems).sortedBy { it.order }

        _uiState.update {
            it.copy(
                allMenuItems = newAllItems,
                filteredMenuItems = categoryItems
            )
        }
    }

    fun saveMenuOrder() {
        viewModelScope.launchSafely(onError = { e ->
            _uiEvent.tryEmit(UiEvent.ShowToast("저장 중 오류: ${e.message}"))
        }) {
            val category = _uiState.value.selectedCategoryIndex
            val itemsToSave = _uiState.value.allMenuItems.filter { it.id / 1000 == category }
            menuRepository.saveMenuOrders(itemsToSave)
            _uiEvent.tryEmit(UiEvent.ShowToast("저장되었습니다."))
            _uiEvent.tryEmit(UiEvent.RefreshMenu)
        }
    }

    suspend fun getNextAvailableId(): Int {
        val category = _uiState.value.selectedCategoryIndex
        return menuRepository.getNextAvailableIdForCategory(category)
    }

    suspend fun getNextAvailablePlacement(): Int {
        val category = _uiState.value.selectedCategoryIndex
        return menuRepository.getNextAvailablePlacementForCategory(category)
    }

    fun addMenu(name: String, price: Int) {
        viewModelScope.launchSafely(onError = { e ->
            _uiEvent.tryEmit(UiEvent.ShowToast("메뉴 추가 중 오류: ${e.message}"))
        }) {
            if (!menuRepository.isValidMenuName(name)) {
                _uiEvent.tryEmit(UiEvent.ShowToast("존재하는 메뉴입니다."))
                return@launchSafely
            }
            // id/placement 채번도 launchSafely 안에서 수행해, 실패가 단일 에러 경로로 처리되게 한다.
            val category = _uiState.value.selectedCategoryIndex
            val id = menuRepository.getNextAvailableIdForCategory(category)
            val placement = menuRepository.getNextAvailablePlacementForCategory(category)
            menuRepository.addMenu(MenuItem(id, name, price, placement, true))
            _uiEvent.tryEmit(UiEvent.ShowToast("메뉴가 추가되었습니다."))
        }
    }

    fun updateMenu(item: MenuItem, newName: String, newPrice: Int) {
        viewModelScope.launchSafely(onError = { e ->
            _uiEvent.tryEmit(UiEvent.ShowToast("메뉴 수정 중 오류: ${e.message}"))
        }) {
            val updated = item.copy(name = newName, price = newPrice)
            // 낙관적 갱신: Firestore 스냅샷 재방출을 기다리지 않고 즉시 새 상태를 방출해 Compose 가 재구성되도록 한다.
            replaceItemInState(updated)
            menuRepository.updateSpecificMenu(updated)
            _uiEvent.tryEmit(UiEvent.ShowToast("메뉴가 수정되었습니다."))
        }
    }

    fun toggleMenuInUse(item: MenuItem) {
        viewModelScope.launchSafely(onError = { e ->
            _uiEvent.tryEmit(UiEvent.ShowToast("메뉴 상태 변경 중 오류: ${e.message}"))
        }) {
            val updated = item.copy(inuse = !item.inuse)
            // 낙관적 갱신: Firestore 스냅샷 재방출을 기다리지 않고 즉시 새 상태를 방출해 Compose 가 재구성되도록 한다.
            replaceItemInState(updated)
            menuRepository.updateSpecificMenu(updated)
            val status = if (updated.inuse) "활성화" else "비활성화"
            _uiEvent.tryEmit(UiEvent.ShowToast("메뉴가 $status 되었습니다."))
        }
    }

    /** allMenuItems 내 동일 id 항목을 새 인스턴스로 교체하고 현재 카테고리 필터를 재적용해 새 상태를 방출한다. */
    private fun replaceItemInState(updated: MenuItem) {
        val newAll = _uiState.value.allMenuItems.map { if (it.id == updated.id) updated else it }
        _uiState.update { it.copy(allMenuItems = newAll) }
        filterMenuByCategory(_uiState.value.selectedCategoryIndex)
    }

}
