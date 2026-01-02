package eloom.holybean.ui.menumanagement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import eloom.holybean.data.model.MenuItem
import eloom.holybean.data.repository.LambdaRepository
import eloom.holybean.data.repository.MenuRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Named

@HiltViewModel
class MenuManagementViewModel @Inject constructor(
    private val menuRepository: MenuRepository,
    private val lambdaRepository: LambdaRepository,
    @Named("IO") private val dispatcher: CoroutineDispatcher
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
        viewModelScope.launch(dispatcher) {
            _uiState.update { it.copy(isLoading = true) }
            menuRepository.getMenuList()
                .map { list -> list.sortedBy { it.order } }
                .catch { e ->
                    _uiEvent.tryEmit(UiEvent.ShowToast("Error loading menu: ${e.message}"))
                    emit(emptyList())
                }
                .collect { menuList ->
                    _uiState.update {
                        it.copy(
                            allMenuItems = menuList,
                            isLoading = false
                        )
                    }
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
        viewModelScope.launch(dispatcher) {
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

    fun addMenu(id: Int, name: String, price: Int, placement: Int) {
        viewModelScope.launch(dispatcher) {
            val isNameValid = menuRepository.isValidMenuName(name)
            if (!isNameValid) {
                _uiEvent.tryEmit(UiEvent.ShowToast("존재하는 메뉴입니다."))
            } else {
                val item = MenuItem(id, name, price, placement, true)
                menuRepository.addMenu(item)
                _uiEvent.tryEmit(UiEvent.ShowToast("메뉴가 추가되었습니다."))
            }
        }
    }

    fun updateMenu(item: MenuItem, newName: String, newPrice: Int) {
        viewModelScope.launch(dispatcher) {
            item.name = newName
            item.price = newPrice
            menuRepository.updateSpecificMenu(item)
            _uiEvent.tryEmit(UiEvent.ShowToast("메뉴가 수정되었습니다."))
        }
    }

    fun toggleMenuInUse(item: MenuItem) {
        viewModelScope.launch(dispatcher) {
            item.inuse = !item.inuse
            menuRepository.updateSpecificMenu(item)
            val status = if (item.inuse) "활성화" else "비활성화"
            _uiEvent.tryEmit(UiEvent.ShowToast("메뉴가 $status 되었습니다."))
        }
    }

    fun saveMenuListToServer() {
        viewModelScope.launch(dispatcher) {
            try {
                _uiState.update { it.copy(isLoading = true) }
                lambdaRepository.saveMenuListToServer(menuRepository.getMenuListSync())
                _uiState.update { it.copy(isLoading = false) }
                _uiEvent.tryEmit(UiEvent.ShowToast("서버에 저장 완료"))
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false) }
                _uiEvent.tryEmit(UiEvent.ShowToast("서버 저장 실패: ${e.message}"))
            }
        }
    }

    fun getMenuListFromServer() {
        viewModelScope.launch(dispatcher) {
            try {
                _uiState.update { it.copy(isLoading = true) }
                val response = lambdaRepository.getLastedSavedMenuList()
                if (response.isEmpty()) {
                    throw Exception("데이터가 올바르지 않습니다.")
                }
                menuRepository.overwriteMenuList(response)
                _uiState.update { it.copy(isLoading = false) }
                _uiEvent.tryEmit(UiEvent.ShowToast("태블릿에 저장 완료"))
                _uiEvent.tryEmit(UiEvent.RefreshMenu)
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false) }
                _uiEvent.tryEmit(UiEvent.ShowToast("데이터 가져오기 실패: ${e.message}"))
            }
        }
    }
}