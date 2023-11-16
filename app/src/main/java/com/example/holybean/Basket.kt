package com.example.holybean

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView

// menu의 data class 정의
data class BasketItem(val id:Int, val name:String, var price:Int, var count:Int, var total:Int)

// 메뉴목록 (RecyclerView)의 어댑터 정의
class BasketAdapter(var BasketList: ArrayList<BasketItem>) : RecyclerView.Adapter<BasketAdapter.BasketHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BasketHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.basket_recycler_view, parent, false)
        return BasketHolder(view)
    }

    override fun onBindViewHolder(holder: BasketHolder, position: Int) {
        val item = BasketList[position]
        holder.basketName.text = item.name
        holder.basketCount.text = item.count.toString()
        holder.basketTotal.text = item.total.toString()
        holder.itemView.setOnClickListener {
            // 여기서 각 item의 click function을 지정해주자.
            Toast.makeText(holder.itemView.context, "Item ID: ${item.id}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun getItemCount(): Int {
        return BasketList.size
    }

    inner class BasketHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val basketName: TextView = itemView.findViewById(R.id.basket_name)
        val basketCount: TextView = itemView.findViewById(R.id.basket_count)
        val basketTotal: TextView = itemView.findViewById(R.id.basket_price)
    }
}
