package com.example.holybean.ui

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.holybean.data.model.MenuItem
import com.example.holybean.network.ApiService
import com.example.holybean.network.RetrofitClient
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class MenuViewModel @Inject constructor() : ViewModel() {

    private val apiService = RetrofitClient.retrofit.create(ApiService::class.java)

    private val _menulist = MutableLiveData<List<MenuItem>?>()
    val menulist get() = _menulist

    fun fetchData(){
        if(_menulist.value == null){
            viewModelScope.launch {
                try {
                    fetchMenuList()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun onUserDataSetChanged(){
        _menulist.value = null
    }

    private suspend fun fetchMenuList(){
        try{
            println("fetchMenuList")
            val response = apiService.getMenuList()
            if(response.isSuccessful){
                println("fetchMenuList success")
                val body = response.body()
                _menulist.postValue(body?.menulist)
            } else {
                println("fetchMenuList fail")
                throw Exception("fetchMenuList fail")
            }
        } catch (e: Exception){
            throw e
        }
    }
}