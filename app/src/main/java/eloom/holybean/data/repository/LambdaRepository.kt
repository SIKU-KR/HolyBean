package eloom.holybean.data.repository

import eloom.holybean.data.model.*
import eloom.holybean.exception.ApiException
import eloom.holybean.network.ApiService
import eloom.holybean.network.RetrofitClient
import org.json.JSONObject
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

class LambdaRepository @Inject constructor() {

    private val apiService = RetrofitClient.retrofit.create(ApiService::class.java)

    private fun getCurrentDate(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd")
        val currentDate = Date()
        return dateFormat.format(currentDate)
    }

    private fun <T> validateResponse(response: Response<T>): T {
        return if (response.isSuccessful) {
            response.body() ?: throw ApiException("Response body is null")
        } else {
            val jsonObj = JSONObject(response.errorBody()?.string() ?: "{}")
            val message = jsonObj.optString("message")
            throw ApiException("Error: ${response.code()} - $message")
        }
    }

    private fun handleException(e: Exception) {
        e.printStackTrace()
    }

    suspend fun getOrderNumber(): Int {
        return try {
            validateResponse(apiService.getOrderNumber()).nextOrderNum ?: -1
        } catch (e: Exception) {
            handleException(e)
            -1
        }
    }

    suspend fun postOrder(data: Order) {
        try {
            validateResponse(apiService.postOrder(data))
        } catch (e: Exception) {
            handleException(e)
        }
    }

    suspend fun getOrdersOfDay(): ArrayList<OrderItem> {
        return try {
            val response = validateResponse(apiService.getOrderOfDay(getCurrentDate()))
            ArrayList(response.map {
                OrderItem(it.orderNum, it.totalAmount, it.orderMethod, it.customerName)
            })
        } catch (e: Exception) {
            handleException(e)
            arrayListOf()
        }
    }

    suspend fun getOrderDetail(date: String, num: Int): ArrayList<OrdersDetailItem> {
        return try {
            val response = validateResponse(apiService.getSpecificOrder(date, num))
            ArrayList(response.orderItems.map {
                OrdersDetailItem(it.itemName, it.quantity, it.subtotal)
            })
        } catch (e: Exception) {
            handleException(e)
            arrayListOf()
        }
    }

    suspend fun deleteOrder(date: String, num: Int): Boolean {
        return try {
            val response = apiService.deleteOrder(date, num)
            validateResponse(response)
            true
        } catch (e: Exception) {
            handleException(e)
            false
        }
    }

    suspend fun getCreditsList(): ArrayList<CreditItem> {
        return try {
            val response = validateResponse(apiService.getAllCreditOrders())
            ArrayList(response.map {
                CreditItem(it.orderNum, it.totalAmount, it.orderDate, it.customerName)
            })
        } catch (e: Exception) {
            handleException(e)
            arrayListOf()
        }
    }

    suspend fun setCreditOrderPaid(date: String, num: Int) {
        try {
            validateResponse(apiService.updateCreditStatus(date, num))
        } catch (e: Exception) {
            handleException(e)
        }
    }

    suspend fun saveMenuListToServer(menulist: ArrayList<MenuItem>) {
        try {
            validateResponse(apiService.postMenuList(menulist))
        } catch (e: Exception) {
            handleException(e)
        }
    }

    suspend fun getLastedSavedMenuList(): ArrayList<MenuItem> {
        return try {
            val response = validateResponse(apiService.getMenuList())
            ArrayList(response.menulist.map {
                MenuItem(it.id, it.name, it.price, it.order, it.inuse)
            })
        } catch (e: Exception) {
            handleException(e)
            arrayListOf()
        }
    }

}