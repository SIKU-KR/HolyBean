package eloom.holybean.ui.credits

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eloom.holybean.data.repository.LambdaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject


@HiltViewModel
class CreditsViewModel @Inject constructor(
    private val lambdaRepository: LambdaRepository
) : ViewModel() {

    private val _deleteState = MutableLiveData<Unit>()
    val deleteState: LiveData<Unit> get() = _deleteState

    fun handleDeleteButton(date: String, num: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            lambdaRepository.setCreditOrderPaid(date, num)
            _deleteState.postValue(Unit)
        }
    }
}