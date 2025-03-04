package com.example.holybean.ui.menumanagement

import android.content.ContentValues
import android.content.Context
import com.example.holybean.data.repository.MenuDB
import com.example.holybean.data.model.MenuItem
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class MenuManagementViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun disableMenu(item: MenuItem) {
        item.inuse = !item.inuse
        updateSpecificMenu(item)
    }

    fun saveMenuChanges(item: MenuItem, newName: String, newPrice: Int) {
        item.name = newName
        item.price = newPrice
        updateSpecificMenu(item)
    }

    fun saveMenuOrders(items: List<MenuItem>) {
        val menuDB = MenuDB.getInstance(context)
        val db = menuDB.writableDatabase

        db.beginTransaction() // Start a database transaction for batch operation
        try {
            items.forEach { item ->
                val contentValues = ContentValues().apply {
                    put(MenuDB.MENU_PLACEMENT, item.order)
                }
                db.update(
                    MenuDB.MENUDB_TABLE,
                    contentValues,
                    "${MenuDB.MENU_ID} = ?",
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


    private fun updateSpecificMenu(item: MenuItem) {
        val menuDB = MenuDB.getInstance(context)
        val db = menuDB.writableDatabase

        val contentValues = ContentValues().apply {
            put(MenuDB.MENU_NAME, item.name)
            put(MenuDB.MENU_PRICE, item.price)
            put(MenuDB.MENU_PLACEMENT, item.order)
            put(MenuDB.MENU_INUSE, if (item.inuse) 1 else 0)
        }

        db.update(
            MenuDB.MENUDB_TABLE,
            contentValues,
            "${MenuDB.MENU_ID} = ?",
            arrayOf(item.id.toString())
        )

        db.close()
    }

    fun getNextAvailableIdForCategory(category: Int): Int {
        val menuDB = MenuDB.getInstance(context)
        val db = menuDB.readableDatabase

        val startRange = category * 1000 + 1
        val endRange = (category + 1) * 1000 - 1

        val query =
            "SELECT ${MenuDB.MENU_ID} FROM ${MenuDB.MENUDB_TABLE} WHERE ${MenuDB.MENU_ID} BETWEEN ? AND ? ORDER BY ${MenuDB.MENU_ID} ASC"
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
        // Return -1 if no ID is available
        return if (nextId <= endRange) nextId else -1
    }

    fun getNextAvailablePlacementForCategory(category: Int): Int {
        val menuDB = MenuDB.getInstance(context)
        val db = menuDB.readableDatabase
        val startRange = category * 1000 + 1
        val endRange = (category + 1) * 1000 - 1
        val query = "SELECT ${MenuDB.MENU_PLACEMENT} FROM ${MenuDB.MENUDB_TABLE} WHERE ${MenuDB.MENU_PLACEMENT} BETWEEN ? AND ? ORDER BY ${MenuDB.MENU_PLACEMENT} ASC"
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
        val menuDB = MenuDB.getInstance(context)
        val db = menuDB.writableDatabase
        val contentValues = ContentValues().apply {
            put(MenuDB.MENU_ID, item.id)
            put(MenuDB.MENU_NAME, item.name)
            put(MenuDB.MENU_PRICE, item.price)
            put(MenuDB.MENU_PLACEMENT, item.order)
            put(MenuDB.MENU_INUSE, if (item.inuse) 1 else 0)
        }
        val newRowId = db.insert(MenuDB.MENUDB_TABLE, null, contentValues)
        if (newRowId == -1L) {
            println("Error inserting new menu item into the database.")
        } else {
            println("New menu item inserted with ID: $newRowId")
        }
        db.close()
    }

    fun isValidMenuName(newName: String): Boolean {
        val menuDB = MenuDB.getInstance(context)
        val db = menuDB.readableDatabase
        val query = "SELECT COUNT(*) FROM ${MenuDB.MENUDB_TABLE} WHERE ${MenuDB.MENU_NAME} = ?"
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


}