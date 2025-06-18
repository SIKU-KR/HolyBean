package eloom.holybean.data.repository

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import eloom.holybean.data.model.MenuItem
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MenuDB @Inject constructor(
    @ApplicationContext context: Context
) {
    private val dbHelper = MenuDBHelper(context)

    fun getMenuList(): List<MenuItem> {
        val menuList = ArrayList<MenuItem>()
        val db = dbHelper.readableDatabase

        try {
            val cursor = db.rawQuery(
                "SELECT id, name, price, placement, inuse FROM ${MenuDBHelper.MENUDB_TABLE}", null
            )
            if (cursor.moveToFirst()) {
                do {
                    val id = cursor.getInt(cursor.getColumnIndexOrThrow("id"))
                    val name = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                    val price = cursor.getInt(cursor.getColumnIndexOrThrow("price"))
                    val placement = cursor.getInt(cursor.getColumnIndexOrThrow("placement"))
                    val inuse = cursor.getInt(cursor.getColumnIndexOrThrow("inuse")) == 1
                    menuList.add(MenuItem(id, name, price, placement, inuse))
                } while (cursor.moveToNext())
            }
            cursor.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return menuList.sortedBy { it.id }
    }

    fun overwriteMenuList(menuList: List<MenuItem>) {
        val db = dbHelper.writableDatabase
        db.beginTransaction() // 트랜잭션 시작
        try {
            // 모든 기존 데이터 삭제
            db.delete(MenuDBHelper.MENUDB_TABLE, null, null)
            // 매개변수로 받은 데이터 모두 삽입
            menuList.forEach { item ->
                val contentValues = ContentValues().apply {
                    put(MenuDBHelper.MENU_ID, item.id)
                    put(MenuDBHelper.MENU_NAME, item.name)
                    put(MenuDBHelper.MENU_PRICE, item.price)
                    put(MenuDBHelper.MENU_PLACEMENT, item.order)
                    put(MenuDBHelper.MENU_INUSE, if (item.inuse) 1 else 0)
                }
                val newRowId = db.insert(MenuDBHelper.MENUDB_TABLE, null, contentValues)
                if (newRowId == -1L) {
                    println("Error inserting new menu item with ID: ${item.id}")
                } else {
                    println("New menu item inserted with ID: $newRowId")
                }
            }
            db.setTransactionSuccessful() // 트랜잭션 커밋
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            db.endTransaction() // 트랜잭션 종료
            db.close()
        }
    }


    fun saveMenuOrders(items: List<MenuItem>) {
        val db = dbHelper.writableDatabase
        db.beginTransaction() // 트랜잭션 시작
        try {
            items.forEach { item ->
                val contentValues = ContentValues().apply {
                    put(MenuDBHelper.MENU_PLACEMENT, item.order)
                }
                db.update(
                    MenuDBHelper.MENUDB_TABLE,
                    contentValues,
                    "${MenuDBHelper.MENU_ID} = ?",
                    arrayOf(item.id.toString())
                )
            }
            db.setTransactionSuccessful()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            db.endTransaction()
        }
        db.close()
    }

    fun updateSpecificMenu(item: MenuItem) {
        val db = dbHelper.writableDatabase
        val contentValues = ContentValues().apply {
            put(MenuDBHelper.MENU_NAME, item.name)
            put(MenuDBHelper.MENU_PRICE, item.price)
            put(MenuDBHelper.MENU_PLACEMENT, item.order)
            put(MenuDBHelper.MENU_INUSE, if (item.inuse) 1 else 0)
        }
        db.update(
            MenuDBHelper.MENUDB_TABLE,
            contentValues,
            "${MenuDBHelper.MENU_ID} = ?",
            arrayOf(item.id.toString())
        )
        db.close()
    }

    fun getNextAvailableIdForCategory(category: Int): Int {
        val db = dbHelper.readableDatabase
        val startRange = category * 1000 + 1
        val endRange = (category + 1) * 1000 - 1

        val query = "SELECT ${MenuDBHelper.MENU_ID} FROM ${MenuDBHelper.MENUDB_TABLE} WHERE ${MenuDBHelper.MENU_ID} BETWEEN ? AND ? ORDER BY ${MenuDBHelper.MENU_ID} ASC"
        val cursor = db.rawQuery(query, arrayOf(startRange.toString(), endRange.toString()))
        var nextId = startRange
        if (cursor.moveToFirst()) {
            do {
                val currentId = cursor.getInt(0)
                if (currentId > nextId) {
                    break
                }
                nextId = currentId + 1
            } while (cursor.moveToNext())
        }
        cursor.close()
        db.close()
        return if (nextId <= endRange) nextId else -1
    }

    fun getNextAvailablePlacementForCategory(category: Int): Int {
        val db = dbHelper.readableDatabase
        val startRange = category * 1000 + 1
        val endRange = (category + 1) * 1000 - 1
        val query = "SELECT ${MenuDBHelper.MENU_PLACEMENT} FROM ${MenuDBHelper.MENUDB_TABLE} WHERE ${MenuDBHelper.MENU_PLACEMENT} BETWEEN ? AND ? ORDER BY ${MenuDBHelper.MENU_PLACEMENT} ASC"
        val cursor = db.rawQuery(query, arrayOf(startRange.toString(), endRange.toString()))
        var nextPlacement = startRange
        if (cursor.moveToFirst()) {
            do {
                val currentPlacement = cursor.getInt(0)
                if (currentPlacement > nextPlacement) {
                    break
                }
                nextPlacement = currentPlacement + 1
            } while (cursor.moveToNext())
        }
        cursor.close()
        db.close()
        return if (nextPlacement <= endRange) nextPlacement else -1
    }

    fun addMenu(item: MenuItem) {
        val db = dbHelper.writableDatabase
        val contentValues = ContentValues().apply {
            put(MenuDBHelper.MENU_ID, item.id)
            put(MenuDBHelper.MENU_NAME, item.name)
            put(MenuDBHelper.MENU_PRICE, item.price)
            put(MenuDBHelper.MENU_PLACEMENT, item.order)
            put(MenuDBHelper.MENU_INUSE, if (item.inuse) 1 else 0)
        }
        val newRowId = db.insert(MenuDBHelper.MENUDB_TABLE, null, contentValues)
        if (newRowId == -1L) {
            println("Error inserting new menu item into the database.")
        } else {
            println("New menu item inserted with ID: $newRowId")
        }
        db.close()
    }

    fun isValidMenuName(newName: String): Boolean {
        val db = dbHelper.readableDatabase
        val query = "SELECT COUNT(*) FROM ${MenuDBHelper.MENUDB_TABLE} WHERE ${MenuDBHelper.MENU_NAME} = ?"
        val cursor = db.rawQuery(query, arrayOf(newName))
        var isValid = true
        if (cursor.moveToFirst()) {
            val count = cursor.getInt(0)
            isValid = count == 0
        }
        cursor.close()
        db.close()
        return isValid
    }

    private class MenuDBHelper(context: Context) : SQLiteOpenHelper(context, MENUDB_NAME, null, MENUDB_VER) {
        companion object {
            const val MENUDB_NAME = "menuDB.db"
            const val MENUDB_VER = 1
            const val MENUDB_TABLE = "menu"
            const val MENU_ID = "id"
            const val MENU_NAME = "name"
            const val MENU_PRICE = "price"
            const val MENU_PLACEMENT = "placement"
            const val MENU_INUSE = "inuse"
        }

        override fun onCreate(db: SQLiteDatabase?) {
            db?.execSQL(
                """
                CREATE TABLE $MENUDB_TABLE (
                    $MENU_ID INTEGER PRIMARY KEY,
                    $MENU_NAME TEXT NOT NULL,
                    $MENU_PRICE INTEGER NOT NULL,
                    $MENU_PLACEMENT INTEGER NOT NULL,
                    $MENU_INUSE INTEGER NOT NULL DEFAULT 1
                )
                """.trimIndent()
            )
        }

        override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
            // 데이터베이스 스키마 변경시 업그레이드 로직 구현
        }
    }
}
