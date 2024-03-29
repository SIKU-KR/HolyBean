package com.example.holybean.common

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.holybean.dataclass.BasketItem
import com.example.holybean.dataclass.CreditItem
import com.example.holybean.dataclass.MenuItem
import com.example.holybean.dataclass.OrderItem
import com.example.holybean.dataclass.OrdersDetailItem
import com.example.holybean.dataclass.ReportDetailItem
import com.opencsv.CSVReaderBuilder
import java.io.BufferedReader
import java.io.FileOutputStream
import java.io.FileReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date

class DatabaseManager private constructor(
    context: Context
) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "database.db"
        private const val DATABASE_VERSION = 1

        private var instance: DatabaseManager? = null

        private fun getInstance(context: Context): DatabaseManager {
            return instance ?: synchronized(this) {
                instance ?: DatabaseManager(context.applicationContext).also {
                    instance = it
                    it.copyDatabaseFromAssets(context, DATABASE_NAME)
                }
            }
        }

        fun getMenuList(): ArrayList<MenuItem> {
            val menuList = ArrayList<MenuItem>()

            try {
                val inputStream = javaClass.classLoader.getResourceAsStream("assets/menu.csv")
                val reader = CSVReaderBuilder(InputStreamReader(inputStream))
                    .withSkipLines(1) // Skip the header row
                    .build()
                reader.readAll().forEach { line ->
                    if (line.size == 4) {
                        val id = line[0].toInt()
                        val name = line[1]
                        val price = line[2].toInt()
                        val placement = line[3].toInt()
                        val menuItem = MenuItem(id, name, price, placement)
                        menuList.add(menuItem)
                    }
                }
                reader.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            menuList.sortBy { it.id }
            return menuList
        }

        fun getOrderList(context: Context, date: String): ArrayList<OrderItem> {
            val instance = getInstance(context)
            return instance.readOrders(date)
        }

        fun getCreditList(context: Context): ArrayList<CreditItem> {
            val instance = getInstance(context)
            return instance.readCredits()
        }

        fun getOrderDetail(context: Context, num: Int, date: String): ArrayList<OrdersDetailItem> {
            val instance = getInstance(context)
            return instance.readOrderDetail(num, date)
        }

        fun getReportData(context: Context, date1: String, date2: String): Map<String, Int> {
            val instance = getInstance(context)
            return instance.makeReportInfo(date1, date2)
        }

        fun getReportDetailData(context: Context, date1: String, date2: String): ArrayList<ReportDetailItem> {
            val instance = getInstance(context)
            return instance.makeReportDetail(date1, date2)
        }

        fun getCurrentOrderNumber(context: Context): Int {
            val instance = getInstance(context)
            val currentDate = instance.getCurrentDate()
            var lastorderId: Int? = null
            val db = instance.readableDatabase
            val query = "SELECT MAX(order_id) FROM Orders WHERE order_date = ?"
            val cursor: Cursor = db.rawQuery(query, arrayOf(currentDate))

            cursor.use {
                if (it.moveToFirst()) {
                    lastorderId = it.getInt(0)
                }
            }
            cursor.close()
            db.close()
            return lastorderId?.plus(1) ?: 1
        }


        fun orderProcess(context: Context, orderId: Int, totalPrice: Int, orderMethod: String, ordererName: String){
            val dbHelper = getInstance(context)
            val currentDate = dbHelper.getCurrentDate()
            val ordersDb = dbHelper.writableDatabase

            // Insert into Orders table
            val ordersValues = ContentValues().apply {
                put("order_id", orderId)
                put("order_date", currentDate)
                put("total_amount", totalPrice)
                put("method", orderMethod)
                put("orderer", ordererName)
            }
            ordersDb.insert("Orders", null, ordersValues)

            if(orderMethod == "외상"){
                val creditDb = dbHelper.writableDatabase
                val creditValues = ContentValues().apply {
                    put("order_id", orderId)
                    put("order_date", currentDate)
                    put("total_amount", totalPrice)
                    put("orderer", ordererName)
                }
                ordersDb.insert("Credits", null, creditValues)
                creditDb.close()
            }

            // Close the databases after use
            ordersDb.close()
        }

        fun orderDetailProcess(context: Context, orderId: Int, basket: ArrayList<BasketItem>) {
            val dbHelper = getInstance(context)
            val currentDate = dbHelper.getCurrentDate()
            val detailsDb = dbHelper.writableDatabase

            // Insert into Details table
            for (basketItem in basket) {
                val detailsValues = ContentValues().apply {
                    put("order_id", orderId)
                    put("date",currentDate)
                    put("product_id", basketItem.id)
                    put("product_name", basketItem.name)
                    put("quantity", basketItem.count)
                    put("price", basketItem.price)
                    put("subtotal", basketItem.total)
                }
                detailsDb.insert("Details", null, detailsValues)
            }
            detailsDb.close()
        }

        fun deleteCreditRecord(context: Context, orderNum: Int, orderDate: String){
            val dbHelper = getInstance(context)
            val creditDb = dbHelper.writableDatabase

            try {
                // Delete from Credits table
                val whereClause = "order_id = ? AND order_date = ?"
                val whereArgs = arrayOf(orderNum.toString(), orderDate)
                creditDb.delete("Credits", whereClause, whereArgs)
                println("Credit record deleted successfully.")
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                // Close the database after use
                creditDb.close()
            }
        }

        fun deleteAnOrder(context: Context, orderNum: Int, orderDate: String){
            val dbHelper = getInstance(context)
            val db = dbHelper.writableDatabase

            try {
                // Delete from Orders table
                val whereClause1 = "order_id = ? AND order_date = ?"
                val whereArgs1 = arrayOf(orderNum.toString(), orderDate)
                db.delete("Orders", whereClause1, whereArgs1)

                // Delete from Details table
                val whereClause2 = "order_id = ? AND date = ?"
                val whereArgs2 = arrayOf(orderNum.toString(), orderDate)
                db.delete("Details", whereClause2, whereArgs2)

            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                db.close()
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) { }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) { }

    private fun copyDatabaseFromAssets(context: Context, databaseName: String) {
        val dbFile = context.getDatabasePath(databaseName)

        if (!dbFile.exists()) {
            dbFile.parentFile?.mkdirs()
            context.assets.open(databaseName).use { inputStream ->
                FileOutputStream(dbFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
        }
    }

    private fun getCurrentDate(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd")
        val currentDate = Date()
        return dateFormat.format(currentDate)
    }

    @SuppressLint("Range")
    private fun readOrders(date: String): ArrayList<OrderItem> {
        val orderList = ArrayList<OrderItem>()
        val db = this.readableDatabase
        val cursor: Cursor = db.rawQuery("SELECT * FROM Orders WHERE order_date = ?", arrayOf(date))
        cursor.use {
            while (it.moveToNext()) {
                val orderId = it.getInt(it.getColumnIndex("order_id"))
                val totalAmount = it.getInt(it.getColumnIndex("total_amount"))
                val method = it.getString(it.getColumnIndex("method"))
                val orderer = it.getString(it.getColumnIndex("orderer"))
                orderList.add(OrderItem(orderId, totalAmount, method, orderer))
            }
        }
        cursor.close()
        db.close()
        orderList.sortBy { it.orderId }
        return orderList
    }

    @SuppressLint("Range")
    private fun readCredits(): ArrayList<CreditItem> {
        val orderList = ArrayList<CreditItem>()
        val db = this.readableDatabase
        val cursor: Cursor = db.rawQuery("SELECT * FROM Credits", null)
        cursor.use {
            while (it.moveToNext()) {
                val orderId = it.getInt(it.getColumnIndex("order_id"))
                val totalAmount = it.getInt(it.getColumnIndex("total_amount"))
                val orderDate = it.getString(it.getColumnIndex("order_date"))
                val orderer = it.getString(it.getColumnIndex("orderer"))
                orderList.add(CreditItem(orderId, totalAmount, orderDate, orderer))
            }
        }
        cursor.close()
        db.close()
        orderList.sortBy { it.date }
        return orderList
    }

    @SuppressLint("Range")
    private fun readOrderDetail(num: Int, date: String): ArrayList<OrdersDetailItem> {
        val orderList = ArrayList<OrdersDetailItem>()
        val db = this.readableDatabase
        val cursor: Cursor = db.rawQuery("SELECT * FROM Details WHERE order_id = ? AND date = ?", arrayOf(num.toString(), date))
        cursor.use{
            while (it.moveToNext()) {
                val id = it.getInt(it.getColumnIndex("id"))
                val name = it.getString(it.getColumnIndex("product_name"))
                val quantity = it.getInt(it.getColumnIndex("quantity"))
                val subtotal = it.getInt(it.getColumnIndex("subtotal"))
                orderList.add(OrdersDetailItem(id, name, quantity, subtotal))
            }
        }
        cursor.close()
        db.close()
        orderList.sortBy { it.id }
        return orderList
    }

    @SuppressLint("Range")
    private fun makeReportInfo(date1: String, date2: String): Map<String, Int> {
        var dataMap = mutableMapOf<String, Int>("전체" to 0, "쿠폰" to 0, "현금" to 0, "계좌이체" to 0, "외상" to 0)
        val db = this.readableDatabase
        val cursor: Cursor = db.rawQuery("SELECT * FROM Orders WHERE order_date Between ? AND ?", arrayOf(date1, date2))
        cursor.use {
            while (it.moveToNext()) {
                val totalAmount = it.getInt(it.getColumnIndex("total_amount"))
                val method = it.getString(it.getColumnIndex("method"))
                dataMap["전체"] = dataMap["전체"]!! + totalAmount
                when (method) {
                    "쿠폰", "현금", "계좌이체", "외상" -> dataMap[method] = dataMap[method]!! + totalAmount
                }
            }
        }
        return dataMap
    }

    @SuppressLint("Range")
    private fun makeReportDetail(date1: String, date2: String): ArrayList<ReportDetailItem> {
        var result = mutableMapOf<Int, Triple<String, Int, Int>>()
        val db = this.readableDatabase
        val cursor: Cursor = db.rawQuery("SELECT * FROM Details WHERE date BETWEEN ? AND ?",arrayOf(date1, date2))
        cursor.use {
            while (it.moveToNext()) {
                val productId = it.getInt(it.getColumnIndex("product_id"))
                val productName = it.getString(it.getColumnIndex("product_name"))
                val quantity = it.getInt(it.getColumnIndex("quantity"))
                val subtotal = it.getInt(it.getColumnIndex("subtotal"))

                // Check if the productName is already in the map
                if (result.containsKey(productId)) {
                    // If yes, update the existing entry by adding the current quantity and subtotal
                    val currentTriple = result[productId]!!
                    val updatedTriple = Triple(productName, currentTriple.second + quantity, currentTriple.third + subtotal)
                    result[productId] = updatedTriple
                } else {
                    // If no, create a new entry with the current quantity and subtotal
                    result[productId] = Triple(productName, quantity, subtotal)
                }
            }
        }
        db.close()

        val menuData = getMenuList().filter {it.price == 0}
        val filterData = menuData.map { it.id }.toSet()
        val filteredResult = result.filterKeys { it !in filterData }.toMutableMap()

        val reportDetailsList = ArrayList(filteredResult.entries.map { entry ->
            ReportDetailItem(entry.key, entry.value.first, entry.value.second, entry.value.third)
        }.sortedBy { reportDetail -> reportDetail.id })

        return reportDetailsList
    }

}
