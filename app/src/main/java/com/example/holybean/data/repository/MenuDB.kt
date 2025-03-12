package com.example.holybean.data.repository

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.holybean.data.model.MenuItem

class MenuDB private constructor(
    context: Context
) : SQLiteOpenHelper(context, MENUDB_NAME, null, MENUDB_VER) {

    companion object {
        @Volatile
        private var INSTANCE: MenuDB? = null

        // db info
        const val MENUDB_NAME = "menuDB.db"
        const val MENUDB_VER = 1
        const val MENUDB_TABLE = "menu"
        const val MENU_ID = "id"
        const val MENU_NAME = "name"
        const val MENU_PRICE = "price"
        const val MENU_PLACEMENT = "placement"
        const val MENU_INUSE = "inuse"

        fun getInstance(context: Context): MenuDB {
            return INSTANCE ?: synchronized(this) {
                val instance = MenuDB(context)
                INSTANCE = instance
                instance
            }
        }

        fun getMenuList(context: Context): ArrayList<MenuItem> {
            val menuList = ArrayList<MenuItem>()
            val db = getInstance(context).readableDatabase

            try {
                val cursor = db.rawQuery("SELECT id, name, price, placement, inuse FROM menu", null)

                if (cursor.moveToFirst()) {
                    do {
                        val id = cursor.getInt(cursor.getColumnIndexOrThrow("id"))
                        val name = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                        val price = cursor.getInt(cursor.getColumnIndexOrThrow("price"))
                        val placement = cursor.getInt(cursor.getColumnIndexOrThrow("placement"))
                        val inuse = cursor.getInt(cursor.getColumnIndexOrThrow("inuse")) == 1  // Convert to boolean
                        val menuItem = MenuItem(id, name, price, placement, inuse)
                        menuList.add(menuItem)
                    } while (cursor.moveToNext())
                }
                cursor.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            menuList.sortBy { it.id }
            return menuList
        }
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
    }


}