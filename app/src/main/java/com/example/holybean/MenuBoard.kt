package com.example.holybean

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.database.Cursor
import android.widget.Toast
import java.io.FileOutputStream

// menu의 data class 정의
data class MenuItem(val id:Int, val name:String, val price:Int)

// 메뉴목록 (RecyclerView)의 어댑터 정의
class MenuAdapter(val itemList: ArrayList<MenuItem>, private val mainListner : MainActivityFunctions) : RecyclerView.Adapter<MenuAdapter.MenuViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MenuViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.menu_recycler_view, parent, false)
        return MenuViewHolder(view)
    }

    override fun onBindViewHolder(holder: MenuViewHolder, position: Int) {
        val item = itemList[position]
        holder.menuName.text = item.name
        holder.menuPrice.text = item.price.toString()
        holder.itemView.setOnClickListener {
            // 여기서 각 item의 click function을 지정해주자.
            Toast.makeText(holder.itemView.context, "Item ID: ${item.id}", Toast.LENGTH_SHORT).show()
            mainListner.addToBasket(item.id)
        }
    }

    override fun getItemCount(): Int {
        return itemList.size
    }

    inner class MenuViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val menuName: TextView = itemView.findViewById(R.id.menu_name)
        val menuPrice: TextView = itemView.findViewById(R.id.menu_price)
    }
}

// menu.db에서 메뉴 읽어오기
@SuppressLint("Range")
fun readMenu(context: Context): ArrayList<MenuItem> {
    copyDatabaseFromAssets(context,"menu.db")
    val dbHelper = object : SQLiteOpenHelper(context, "menu.db", null, 1) {
        override fun onCreate(db: SQLiteDatabase) {}
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}
    }
    val menuList = ArrayList<MenuItem>()
    val db = dbHelper.readableDatabase
    val cursor: Cursor = db.rawQuery("SELECT * FROM Menu", null)
    if (cursor.moveToFirst()) {
        do {
            val id = cursor.getInt(cursor.getColumnIndex("id"))
            val name = cursor.getString(cursor.getColumnIndex("name"))
            val price = cursor.getInt(cursor.getColumnIndex("price"))
            menuList.add(MenuItem(id, name, price))
        } while (cursor.moveToNext())
    }
    cursor.close()
    db.close()
    dbHelper.close()
    return menuList
}

// assets 폴더에서 앱 내부저장소로 db를 복사하는 함수 (필요하다고함)
fun copyDatabaseFromAssets(context: Context, databaseName: String) {
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
