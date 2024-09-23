package com.example.holybean.home

import com.example.holybean.common.MainActivityListener
import com.example.holybean.home.dto.BasketItem
import com.example.holybean.home.dto.OrderData
import com.example.holybean.home.dto.OrderDataWithDualMethod
import java.text.SimpleDateFormat
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import kotlin.concurrent.thread

class HomeService @Inject constructor(
    private val repository: HomeRepository
) : OrderDialogListener {

    private var mainListener: MainActivityListener? = null

    fun setMainActivityListener(listener: MainActivityListener) {
        this.mainListener = listener
    }

    fun getCurrentDate(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd")
        val currentDate = Date()
        return dateFormat.format(currentDate)
    }

    // Calculating total price business logic
    fun getTotal(basketList: ArrayList<BasketItem>): Int {
        var totalPrice = 0
        for (item in basketList) {
            item.total = item.count * item.price
            totalPrice += item.total
        }
        return totalPrice
    }

    fun getUUID(): String {
        val uuid = UUID.randomUUID().toString()
        return uuid
    }

    fun getCurrentOrderNum(): Int {
        val date = getCurrentDate()
        var orderNum = repository.getLastOrderNum(date) + 1
        return orderNum
    }

    override fun onOrderConfirmed(data: OrderData) {
        // 무료 제공 case
        if (data.orderMethod == "무료제공") {
            data.totalPrice = 0
            data.basketList.forEach { it.total = 0 }
        }

        printReceipt(data)

        val rowId = repository.insertToOrders(data)
        repository.insertToDetails(data)

        mainListener?.replaceHomeFragment()
    }

    override fun onOrderConfirmed(data: OrderDataWithDualMethod) {
        val receiptData = OrderData(
            data.basketList,
            data.orderNum,
            data.totalPrice,
            data.takeOption,
            data.customer,
            "${data.firstMethod}+${data.secondMethod}",
            data.date,
            this.getUUID()
        )
        printReceipt(receiptData)

        val uuid = this.getUUID()
        val firstData = OrderData(
            data.basketList,
            data.orderNum,
            data.firstAmount,
            data.takeOption,
            data.customer,
            data.firstMethod,
            data.date,
            uuid
        )
        val secondData = OrderData(
            data.basketList,
            data.orderNum,
            data.secondAmount,
            data.takeOption,
            data.customer,
            data.secondMethod,
            data.date,
            uuid
        )

        val firstRowId = repository.insertToOrders(firstData)
        val secondRowId = repository.insertToOrders(secondData)
        repository.insertToDetails(firstData)

        mainListener?.replaceHomeFragment()
    }

    private fun printReceipt(data: OrderData) {
        val printer = HomePrinter()
        val receiptForCustomer = printer.receiptTextForCustomer(data)
        val receiptForPOS = printer.receiptTextForPOS(data)

        thread {
            printer.print(receiptForCustomer)
            Thread.sleep(500)
            printer.print(receiptForPOS)
            Thread.sleep(2000)
            printer.disconnect()
        }
    }

}