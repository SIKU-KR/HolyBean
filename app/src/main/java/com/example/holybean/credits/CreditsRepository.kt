package com.example.holybean.credits

import android.annotation.SuppressLint
import android.content.Context
import android.database.Cursor
import com.example.holybean.common.Database
import com.example.holybean.credits.dto.CreditItem
import com.example.holybean.orders.dto.OrdersDetailItem
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class CreditsRepository @Inject constructor(
    @ApplicationContext private val context: Context
){
    @SuppressLint("Range")
    fun readCredits(): ArrayList<CreditItem> {
        val orderList = ArrayList<CreditItem>()
        val dbHelper = Database.getInstance(context)
        val db = dbHelper.readableDatabase
        val cursor: Cursor = db.rawQuery("SELECT * FROM ${Database.ORDERS} WHERE ${Database.ORDERS_METHOD} = '외상' ORDER BY ${Database.ORDERS_DATE}", null)
        cursor.use {
            while(it.moveToNext()) {
                val rowId = it.getLong(it.getColumnIndex(Database.ORDERS_ID))
                val num = it.getInt(it.getColumnIndex(Database.ORDERS_NUM))
                val amount = it.getInt(it.getColumnIndex(Database.ORDERS_TOTAL_AMOUNT))
                val orderDate = it.getString(it.getColumnIndex(Database.ORDERS_DATE))
                val customer = it.getString(it.getColumnIndex(Database.ORDERS_CUSTOMER))
                orderList.add(CreditItem(rowId, num, amount, orderDate, customer))
            }
        }
        db.close()
        return orderList
    }

    fun deleteCredits(rowId: Long) {
        val dbHelper = Database.getInstance(context)
        val db = dbHelper.writableDatabase
        val whereClause1 = "${Database.ORDERS_ID} = ?"
        val whereClause2 = "${Database.DETAILS_ORDER_ID} = ?"

        try {
            db.beginTransaction()
            db.delete(Database.ORDERS, whereClause1, arrayOf(rowId.toString()))
            db.delete(Database.DETAILS, whereClause2, arrayOf(rowId.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }

        db.close()
    }

    @SuppressLint("Range")
    fun readOrderDetail(id: Long): ArrayList<OrdersDetailItem> {
        val orderDetailList = ArrayList<OrdersDetailItem>()
        val dbHelper = Database.getInstance(context)
        val db = dbHelper.readableDatabase
        val query = "SELECT * FROM ${Database.DETAILS} WHERE ${Database.DETAILS_ORDER_ID} = ?"
        val cursor: Cursor = db.rawQuery(query, arrayOf(id.toString()))
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
}