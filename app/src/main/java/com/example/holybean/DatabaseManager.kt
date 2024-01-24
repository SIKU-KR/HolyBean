import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.holybean.BasketItem
import com.example.holybean.MenuItem
import com.example.holybean.OrderItem
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date

class DatabaseManager private constructor(
    context: Context
) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "database.db"
        private const val DATABASE_VERSION = 1

        private var instance: DatabaseManager? = null

        fun getInstance(context: Context): DatabaseManager {
            return instance ?: synchronized(this) {
                instance ?: DatabaseManager(context.applicationContext).also {
                    instance = it
                    it.copyDatabaseFromAssets(context, DATABASE_NAME)
                }
            }
        }

        fun getMenuList(context: Context): ArrayList<MenuItem> {
            val instance = getInstance(context)
            return instance.readMenu()
        }

        fun getOrderList(context: Context): ArrayList<OrderItem> {
            val instance = getInstance(context)
            return instance.readOrders()
        }

        fun getCurrentOrderNumber(context: Context): Int {
            val currentDate = getInstance(context).getCurrentDate()
            val db = getInstance(context).readableDatabase
            val cursor: Cursor = db.rawQuery("SELECT COUNT(*) FROM Orders WHERE order_date = ?", arrayOf(currentDate))
            var orderCount = 0
            if (cursor.moveToFirst()) {
                orderCount = cursor.getInt(0)
            }
            cursor.close()
            db.close()
            return orderCount + 1
        }

        fun orderDataProcess(context: Context, orderId: Int, totalPrice: Int, orderMethod: String, ordererName: String, basket: ArrayList<BasketItem>){
            val currentDate = getInstance(context).getCurrentDate()
            val dbHelper = getInstance(context)

            // Insert into Orders table
            val ordersValues = ContentValues().apply {
                put("order_id", orderId)
                put("order_date", currentDate)
                put("total_amount", totalPrice)
                put("method", orderMethod)
                put("orderer", ordererName)
            }

            val ordersDb = dbHelper.writableDatabase
            val orderIdInserted = ordersDb.insert("Orders", null, ordersValues)

            val detailsDb = dbHelper.writableDatabase

            for (basketItem in basket) {
                val detailsValues = ContentValues().apply {
                    put("order_id", orderIdInserted)
                    put("product_id", basketItem.id)
                    put("product_name", basketItem.name)
                    put("quantity", basketItem.count)
                    put("price", basketItem.price)
                    put("subtotal", basketItem.total)
                }
                detailsDb.insert("Details", null, detailsValues)
            }

            // Close the databases after use
            ordersDb.close()
            detailsDb.close()
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

    @SuppressLint("Range")
    private fun readMenu(): ArrayList<MenuItem> {
        val menuList = ArrayList<MenuItem>()
        val db = this.readableDatabase
        val cursor: Cursor = db.rawQuery("SELECT * FROM products", null)
        if (cursor.moveToFirst()) {
            do {
                val id = cursor.getInt(cursor.getColumnIndex("product_id"))
                val name = cursor.getString(cursor.getColumnIndex("name"))
                val price = cursor.getInt(cursor.getColumnIndex("price"))
                menuList.add(MenuItem(id, name, price))
            } while (cursor.moveToNext())
        }
        cursor.close()
        db.close()
        menuList.sortBy { it.id }
        return menuList
    }

    @SuppressLint("Range")
    private fun readOrders(): ArrayList<OrderItem> {
        val currentDate = getCurrentDate()
        val orderList = ArrayList<OrderItem>()
        val db = this.readableDatabase
        val cursor: Cursor = db.rawQuery("SELECT * FROM Orders WHERE order_date = ?", arrayOf(currentDate))

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

    private fun getCurrentDate(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd")
        val currentDate = Date()
        return dateFormat.format(currentDate)
    }
}
