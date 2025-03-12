package com.example.holybean.ui

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.holybean.data.model.MenuItem
import com.example.holybean.data.repository.LambdaRepository
import com.example.holybean.network.ApiService
import com.example.holybean.network.RetrofitClient
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class MenuViewModel @Inject constructor(
    private val lambdaRepository: LambdaRepository,
) : ViewModel() {

    private val _menulist = MutableLiveData<List<MenuItem>?>()
    val menulist get() = _menulist

    fun fetchData(){
        if(_menulist.value == null){
            viewModelScope.launch {
                _menulist.postValue(lambdaRepository.getMenuList())
            }
        }
    }

    fun onUserDataSetChanged(){
        _menulist.value = null
    }
}