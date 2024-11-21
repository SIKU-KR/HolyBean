package com.example.holybean.ui.orderlist

import com.example.holybean.data.repository.OrdersRepository
import com.example.holybean.data.model.OrderItem
import com.example.holybean.data.model.OrdersDetailItem
import com.example.holybean.printer.OrdersPrinter
import java.text.SimpleDateFormat
import java.util.Date
import javax.inject.Inject
import kotlin.concurrent.thread

class OrdersService @Inject constructor(
    private val repository: OrdersRepository
){
    fun getCurrentDate(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd")
        val currentDate = Date()
        return dateFormat.format(currentDate)
    }

    fun reprint(orderNum: Int, basketList: ArrayList<OrdersDetailItem>) {
        val printer = OrdersPrinter()
        val text = printer.makeText(orderNum, basketList)
        thread {
            printer.print(text)
            Thread.sleep(2000)
            printer.disconnect()
        }
    }

    fun getOrderList(): ArrayList<OrderItem> {
        val date = getCurrentDate()
        return repository.readOrderList(date)
    }

    fun getOrderDetail(id: String): ArrayList<OrdersDetailItem> {
        return repository.readOrderDetail(id)
    }

    fun deleteOrder(id: String, num: Int){
        val date = getCurrentDate()
        repository.deleteOrder(id, num, date)
    }
}