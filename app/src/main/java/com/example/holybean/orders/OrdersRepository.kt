package com.example.holybean.orders

import android.annotation.SuppressLint
import android.content.Context
import android.database.Cursor
import com.example.holybean.common.Database
import com.example.holybean.orders.dto.OrderItem
import com.example.holybean.orders.dto.OrdersDetailItem
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class OrdersRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    @SuppressLint("Range")
    fun readOrderList(date: String): ArrayList<OrderItem> {
        val orderList = ArrayList<OrderItem>()
        val dbHelper = Database.getInstance(context)
        val db = dbHelper.readableDatabase
        val query = "SELECT * FROM ${Database.ORDERS} WHERE ${Database.ORDERS_DATE} = ? ORDER BY ${Database.ORDERS_NUM}"
        val cursor: Cursor = db.rawQuery(query, arrayOf(date))
        cursor.use {
            while(it.moveToNext()) {
                val uuid = it.getString(it.getColumnIndex(Database.ORDERS_UUID))
                val num = it.getInt(it.getColumnIndex(Database.ORDERS_NUM))
                val totalAmount = it.getInt(it.getColumnIndex(Database.ORDERS_TOTAL_AMOUNT))
                val method = it.getString(it.getColumnIndex(Database.ORDERS_METHOD))
                val customer = it.getString(it.getColumnIndex(Database.ORDERS_CUSTOMER))
                orderList.add(OrderItem(uuid, num, totalAmount, method, customer))
            }
        }
        db.close()
        return orderList
    }

    @SuppressLint("Range")
    fun readOrderDetail(id: String): ArrayList<OrdersDetailItem> {
        val orderDetailList = ArrayList<OrdersDetailItem>()
        val dbHelper = Database.getInstance(context)
        val db = dbHelper.readableDatabase
        val query = "SELECT * FROM ${Database.DETAILS} WHERE ${Database.DETAILS_ORDER_ID} = ?"
        val cursor: Cursor = db.rawQuery(query, arrayOf(id))
        cursor.use {
            while(it.moveToNext()) {
                val id = it.getInt(it.getColumnIndex(Database.DETAILS_PRODUCT_ID))
                val name = it.getString(it.getColumnIndex(Database.DETAILS_PRODUCT_NAME))
                val quantity = it.getInt(it.getColumnIndex(Database.DETAILS_QUANTITY))
                val price = it.getInt(it.getColumnIndex(Database.DETAILS_PRICE))
                val subtotal = price * quantity
                orderDetailList.add(OrdersDetailItem(id, name, quantity, subtotal))
            }
        }
        db.close()
        orderDetailList.sortBy { it.id }
        return orderDetailList
    }

    fun deleteOrder(id: String, num: Int, date: String){
        val dbHelper = Database.getInstance(context)
        val db = dbHelper.writableDatabase
        val whereClause1 = "${Database.ORDERS_NUM} = ? AND ${Database.ORDERS_DATE} = ?"
        val whereClause2 = "${Database.DETAILS_ORDER_ID} = ?"
        val maxRetries = 3
        var attempt = 0

        while (attempt < maxRetries) {
            attempt++
            try {
                db.beginTransaction()
                db.delete(Database.ORDERS, whereClause1, arrayOf(num.toString(), date))
                db.delete(Database.DETAILS, whereClause2, arrayOf(id))
                db.setTransactionSuccessful()
                break
            } catch (e: Exception) {
                // 예외 처리 (예: 로그 출력)
                e.printStackTrace()
                if (attempt >= maxRetries) {
                    throw e  // 최대 재시도 횟수에 도달한 경우 예외를 다시 던짐
                }
            } finally {
                db.endTransaction()
            }
        }
        db.close()
    }


}