package com.example.holybean.data.repository

import android.annotation.SuppressLint
import android.content.Context
import android.database.Cursor
import com.example.holybean.data.model.ReportDetailItem
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class ReportRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun makeReport(date1: String, date2: String): Map<String, Int> {
        val dataOfAll = readOrders(date1, date2)
        val dataOfCoupon = readCouponSales(date1, date2)

        // Calculate the difference between dataOfAll and dataOfCoupon for each key
        val report = mutableMapOf<String, Int>()
        for (key in dataOfAll.keys) {
            val totalAll = dataOfAll[key] ?: 0
            val totalCoupon = dataOfCoupon[key] ?: 0
            report[key] = totalAll - totalCoupon
        }

        return report
    }


    @SuppressLint("Range")
    fun readOrders(date1: String, date2: String): Map<String, Int> {
        val dbHelper = Database.getInstance(context)
        val db = dbHelper.readableDatabase

        var dataMap = mutableMapOf<String, Int>("전체" to 0, "쿠폰" to 0, "현금" to 0, "계좌이체" to 0, "외상" to 0)
        val cursor: Cursor = db.rawQuery("SELECT * FROM ${Database.ORDERS} WHERE ${Database.ORDERS_DATE} Between ? AND ?", arrayOf(date1, date2))
        cursor.use {
            while (it.moveToNext()) {
                val totalAmount = it.getInt(it.getColumnIndex(Database.ORDERS_TOTAL_AMOUNT))
                val method = it.getString(it.getColumnIndex(Database.ORDERS_METHOD))
                dataMap["전체"] = dataMap["전체"]!! + totalAmount
                when (method) {
                    "쿠폰", "현금", "계좌이체", "외상" -> dataMap[method] = dataMap[method]!! + totalAmount
                }
            }
        }
        db.close()
        return dataMap
    }

    @SuppressLint("Range")
    fun readCouponSales(date1: String, date2: String): Map<String, Int> {
        var dataMap = mutableMapOf<String, Int>("전체" to 0, "쿠폰" to 0, "현금" to 0, "계좌이체" to 0, "외상" to 0)
        val dbHelper = Database.getInstance(context)
        val db = dbHelper.readableDatabase
        val query = """
        SELECT o.method, (d.price * d.quantity) AS total_price
        FROM Orders AS o
        JOIN Details AS d ON o.uuid = d.uuid
        WHERE d.product_id = 999 AND o.date BETWEEN ? AND ?;
        """.trimIndent()
        val cursor: Cursor = db.rawQuery(query, arrayOf(date1, date2))
        cursor.use {
            while (it.moveToNext()) {
                val method = it.getString(it.getColumnIndex("o.method"))
                val total_price = it.getInt(it.getColumnIndex("total_price"))
                dataMap["전체"] = dataMap["전체"]!! + total_price
                when (method) {
                    "쿠폰", "현금", "계좌이체", "외상" -> dataMap[method] = dataMap[method]!! + total_price
                }
            }
        }
        db.close()
        return dataMap
    }

    @SuppressLint("Range")
    fun makeReportDetail(date1: String, date2: String): ArrayList<ReportDetailItem> {
        val resultList = ArrayList<ReportDetailItem>()
        val dbHelper = Database.getInstance(context)
        val db = dbHelper.readableDatabase
        val query = """
        SELECT product_name, SUM(quantity) AS quantity, SUM(price * quantity) AS total_price
        FROM (
            SELECT DISTINCT d.uuid, d.product_name, d.quantity, d.price
            FROM Details AS d
            JOIN Orders AS o ON d.uuid = o.uuid
            WHERE o.date BETWEEN ? AND ?
        ) AS distinct_results
        GROUP BY product_name;
        """.trimIndent()
        val cursor: Cursor = db.rawQuery(query, arrayOf(date1, date2))

        cursor.use {
            while (it.moveToNext()) {
                val productName = it.getString(it.getColumnIndex("d.product_name"))
                val quantity = it.getInt(it.getColumnIndex("quantity"))
                val price = it.getInt(it.getColumnIndex("total_price"))
                resultList.add(ReportDetailItem(productName, quantity, price))
            }
        }
        return resultList
    }


}