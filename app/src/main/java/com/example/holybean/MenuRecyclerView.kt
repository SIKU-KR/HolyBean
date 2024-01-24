package com.example.holybean

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import android.widget.Toast

// menu의 data class 정의
data class MenuItem(val id:Int, val name:String, val price:Int)

// 메뉴목록 (RecyclerView)의 어댑터 정의
class MenuAdapter(private val itemList: ArrayList<MenuItem>, private val mainListner : HomeFunctions) : RecyclerView.Adapter<MenuAdapter.MenuViewHolder>() {

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
            Toast.makeText(holder.itemView.context, "메뉴 추가 : ${item.name}", Toast.LENGTH_SHORT).show()
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