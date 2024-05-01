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
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date

class DatabaseManager private constructor(
    context: Context
) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "database.db"
        private const val DATABASE_VERSION = 1

        // 테이블 정의
        // 테이블 이름 정의
        const val ORDERS = "Orders"
        const val CREDITS = "Credits"
        const val DETAILS = "Details"

        // Orders 테이블
        const val ORDERS_ID = "id"
        const val ORDERS_ORDER_ID = "order_id"
        const val ORDERS_DATE = "order_date"
        const val ORDERS_TOTAL_AMOUNT = "total_amount"
        const val ORDERS_METHOD = "method"
        const val ORDERS_ORDERER = "orderer"

        // Credits 테이블
        const val CREDITS_ID = "id"
        const val CREDITS_ORDER_ID = "order_id"
        const val CREDITS_DATE = "order_date"
        const val CREDITS_TOTAL_AMOUNT = "total_amount"
        const val CREDITS_ORDERER = "orderer"

        // Details 테이블 컬럼
        const val DETAILS_ID = "id"
        const val DETAILS_ORDER_ID = "order_id"
        const val DETAILS_DATE = "date"
        const val DETAILS_PRODUCT_ID = "product_id"
        const val DETAILS_PRODUCT_NAME = "product_name"
        const val DETAILS_QUANTITY = "quantity"
        const val DETAILS_PRICE = "price"
        const val DETAILS_SUBTOTAL = "subtotal"

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
            val query = "SELECT MAX($ORDERS_ORDER_ID) FROM $ORDERS WHERE $ORDERS_DATE = ?"
            val cursor: Cursor = db.rawQuery(query, arrayOf(currentDate))
            cursor.use {
                if (it.moveToFirst()) {
                    lastorderId = it.getInt(0)
                }
            }
            db.close()
            return lastorderId?.plus(1) ?: 1
        }

        fun orderProcess(context: Context, orderId: Int, totalPrice: Int, orderMethod: String, ordererName: String){
            val dbHelper = getInstance(context)
            val currentDate = dbHelper.getCurrentDate()
            val db = dbHelper.writableDatabase

            try {
                // db 트랜잭션 시작
                db.beginTransaction()
                // Orders 테이블에는 항상 Insert
                val ordersValues = ContentValues().apply {
                    put(ORDERS_ORDER_ID, orderId)
                    put(ORDERS_DATE, currentDate)
                    put(ORDERS_TOTAL_AMOUNT, totalPrice)
                    put(ORDERS_METHOD, orderMethod)
                    put(ORDERS_ORDERER, ordererName)
                }
                db.insert(ORDERS, null, ordersValues)
                // Credits 테이블에는 외상일때만 Insert
                if(orderMethod == "외상"){
                    val creditValues = ContentValues().apply {
                        put(CREDITS_ORDER_ID, orderId)
                        put(CREDITS_DATE, currentDate)
                        put(CREDITS_TOTAL_AMOUNT, totalPrice)
                        put(CREDITS_ORDERER, ordererName)
                    }
                    db.insert(CREDITS, null, creditValues)
                }
                // 트랜잭션 종류
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            db.close()
            /*
            * 함수 개선 사항 : 실패하면 롤백되는데, 재시도하여서 DB에 정상작동할 방법을 적용하지 않았다.
            * 나중에 서버로 데이터를 옮기게 된다면 HTTP로 만들어서 관련 처리를 방안을 고려해보면 좋을듯.
            */
        }

        fun orderDetailProcess(context: Context, orderId: Int, basket: ArrayList<BasketItem>) {
            val dbHelper = getInstance(context)
            val currentDate = dbHelper.getCurrentDate()
            val detailsDb = dbHelper.writableDatabase

            // Insert into Details table
            for (basketItem in basket) {
                val detailsValues = ContentValues().apply {
                    put(DETAILS_ORDER_ID, orderId)
                    put(DETAILS_DATE,currentDate)
                    put(DETAILS_PRODUCT_ID, basketItem.id)
                    put(DETAILS_PRODUCT_NAME, basketItem.name)
                    put(DETAILS_QUANTITY, basketItem.count)
                    put(DETAILS_PRICE, basketItem.price)
                    put(DETAILS_SUBTOTAL, basketItem.total)
                }
                detailsDb.insert(DETAILS, null, detailsValues)
            }
            detailsDb.close()

            /*
            * 이 함수는 나중에 서버화 시킬 때, 위에 Order Process와 하나의 함수로 만들어서 트랜잭션의 성능을 확실히 하자.
            */
        }

        fun deleteCreditRecord(context: Context, orderNum: Int, orderDate: String){
            val dbHelper = getInstance(context)
            val creditDb = dbHelper.writableDatabase

            try {
                // Delete from Credits table
                val whereClause = "$CREDITS_ORDER_ID = ? AND $CREDITS_DATE = ?"
                val whereArgs = arrayOf(orderNum.toString(), orderDate)
                creditDb.delete(CREDITS, whereClause, whereArgs)
                // println("Credit record deleted successfully.")
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                // Close the database after use
                creditDb.close()
            }
            /*
            * 효율적으로 try - catch 문을 쓰자..
            */
        }

        fun deleteAnOrder(context: Context, orderNum: Int, orderDate: String){
            val dbHelper = getInstance(context)
            val db = dbHelper.writableDatabase

            try {
                // Delete from Orders table
                val whereClause1 = "$ORDERS_ORDER_ID = ? AND $ORDERS_DATE = ?"
                val whereArgs1 = arrayOf(orderNum.toString(), orderDate)
                db.delete(ORDERS, whereClause1, whereArgs1)

                // Delete from Details table
                val whereClause2 = "$DETAILS_ORDER_ID = ? AND $DETAILS_DATE = ?"
                val whereArgs2 = arrayOf(orderNum.toString(), orderDate)
                db.delete(DETAILS, whereClause2, whereArgs2)

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
        val cursor: Cursor = db.rawQuery("SELECT * FROM $ORDERS WHERE $ORDERS_DATE = ? ORDER BY $ORDERS_ORDER_ID", arrayOf(date))
        cursor.use {
            while (it.moveToNext()) {
                val orderId = it.getInt(it.getColumnIndex(ORDERS_ORDER_ID))
                val totalAmount = it.getInt(it.getColumnIndex(ORDERS_TOTAL_AMOUNT))
                val method = it.getString(it.getColumnIndex(ORDERS_METHOD))
                val orderer = it.getString(it.getColumnIndex(ORDERS_ORDERER))
                orderList.add(OrderItem(orderId, totalAmount, method, orderer))
            }
        }
        db.close()
        return orderList
    }

    @SuppressLint("Range")
    private fun readCredits(): ArrayList<CreditItem> {
        val orderList = ArrayList<CreditItem>()
        val db = this.readableDatabase
        val cursor: Cursor = db.rawQuery("SELECT * FROM $CREDITS ORDER BY $CREDITS_DATE", null)
        cursor.use {
            while (it.moveToNext()) {
                val orderId = it.getInt(it.getColumnIndex(CREDITS_ORDER_ID))
                val totalAmount = it.getInt(it.getColumnIndex(CREDITS_TOTAL_AMOUNT))
                val orderDate = it.getString(it.getColumnIndex(CREDITS_DATE))
                val orderer = it.getString(it.getColumnIndex(CREDITS_ORDERER))
                orderList.add(CreditItem(orderId, totalAmount, orderDate, orderer))
            }
        }
        db.close()
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
        val resultList = ArrayList<ReportDetailItem>()
        val db = this.readableDatabase
        val query = """
        SELECT MIN($DETAILS_PRODUCT_ID) AS min_id, $DETAILS_PRODUCT_NAME, SUM($DETAILS_QUANTITY) AS total_quantity, SUM($DETAILS_SUBTOTAL) AS total_subtotal
        FROM $DETAILS
        WHERE $DETAILS_DATE BETWEEN ? AND ?
        GROUP BY $DETAILS_PRODUCT_ID, $DETAILS_PRODUCT_NAME
        ORDER BY min_id;
        """.trimIndent()
        val cursor: Cursor = db.rawQuery(query, arrayOf(date1, date2))

        cursor.use {
            while (it.moveToNext()) {
                val productId = it.getInt(it.getColumnIndex("min_id"))
                val productName = it.getString(it.getColumnIndex(DETAILS_PRODUCT_NAME))
                val totalQuantity = it.getInt(it.getColumnIndex("total_quantity"))
                val totalSubtotal = it.getInt(it.getColumnIndex("total_subtotal"))
                resultList.add(ReportDetailItem(productId, productName, totalQuantity, totalSubtotal))
            }
        }
        db.close()
        resultList.removeIf { it.subtotal == 0 }
        return resultList
    }

}
