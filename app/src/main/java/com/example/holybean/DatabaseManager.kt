import android.annotation.SuppressLint
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.holybean.MenuItem
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class DatabaseManager private constructor(
    context: Context
) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "database.db"
        private const val DATABASE_VERSION = 1
        // private val TABLE_NAME = today

        private var instance: DatabaseManager? = null

        fun getInstance(context: Context): DatabaseManager {
            if (instance == null) {
                instance = DatabaseManager(context.applicationContext)
                instance!!.copyDatabaseFromAssets(context, DATABASE_NAME)
            }
            return instance!!
        }

        fun getMenuList(context: Context): ArrayList<MenuItem> {
            var menuList: ArrayList<MenuItem>
            val instance = getInstance(context)
            menuList = instance.readMenu()
            return menuList!!
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
    fun readMenu(): ArrayList<MenuItem> {
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
        menuList.sortBy{it.id}
        return menuList
    }
}
