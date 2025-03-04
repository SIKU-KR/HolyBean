package com.example.holybean.ui

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.holybean.data.model.Menu
import com.example.holybean.network.LambdaConnection
import com.example.holybean.network.RetrofitClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch

@HiltViewModel
class MenuViewModel: ViewModel() {

    private val lambdaConnection = RetrofitClient.retrofit.create(LambdaConnection::class.java)

    private val _menulist = MutableLiveData<List<Menu>?>()
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
            val response = lambdaConnection.getMenuList()
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