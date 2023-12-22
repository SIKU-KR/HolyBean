package com.example.holybean

import android.annotation.SuppressLint
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.FileOutputStream

// menulist binarysearch
fun searchMenuItem(menuItems: ArrayList<MenuItem>, itemId: Int): MenuItem? {
    var low = 0
    var high = menuItems.size - 1
    while (low <= high) {
        val mid = (low + high) / 2
        val midVal = menuItems[mid]
        when {
            midVal.id < itemId -> low = mid + 1
            midVal.id > itemId -> high = mid - 1
            else -> return midVal
        }
    }
    return null // itemId not found
}
