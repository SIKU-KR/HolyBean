package com.example.holybean.home

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import com.example.holybean.common.Database
import com.example.holybean.home.dto.OrderData
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class HomeRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun getLastOrderNum(date: String): Int {
        var lastorderId: Int? = null
        val dbHelper = Database.getInstance(context)
        val db = dbHelper.readableDatabase
        val query =
            "SELECT MAX(${Database.ORDERS_NUM}) FROM ${Database.ORDERS} WHERE ${Database.ORDERS_DATE} = ?"
        val cursor: Cursor = db.rawQuery(query, arrayOf(date))
        cursor.use {
            if (it.moveToFirst()) {
                lastorderId = it.getInt(0)
            }
        }
        db.close()
        return lastorderId ?: 0
    }

    fun insertToOrders(data: OrderData): Long {
        val dbHelper = Database.getInstance(context)
        val db = dbHelper.writableDatabase
        val insertValue = ContentValues().apply {
            put(Database.ORDERS_NUM, data.orderNum)
            put(Database.ORDERS_DATE, data.date)
            put(Database.ORDERS_TOTAL_AMOUNT, data.totalPrice)
            put(Database.ORDERS_METHOD, data.orderMethod)
            put(Database.ORDERS_CUSTOMER, data.customer)
        }
        val rowId = db.insert(Database.ORDERS, null, insertValue)
        db.close()
        return rowId
    }

    fun insertToDetails(rowId: Long, data: OrderData) {
        val dbHelper = Database.getInstance(context)
        val db = dbHelper.writableDatabase
        var insertValue: ContentValues? = null
        val maxRetries = 3
        var attempt = 0

        while (attempt < maxRetries) {
            attempt++
            try {
                db.beginTransaction()
                for (item in data.basketList) {
                    insertValue = ContentValues().apply {
                        put(Database.DETAILS_ORDER_ID, rowId)
                        put(Database.DETAILS_PRODUCT_ID, item.id)
                        put(Database.DETAILS_PRODUCT_NAME, item.name)
                        put(Database.DETAILS_QUANTITY, item.count)
                        put(Database.DETAILS_PRICE, item.price)
                    }
                    db.insert(Database.DETAILS, null, insertValue)
                }
                db.setTransactionSuccessful()
                break  // 트랜잭션이 성공적으로 완료되면 루프를 빠져나감
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
