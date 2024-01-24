package com.example.holybean

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView

data class BasketItem(
    val id: Int,
    val name: String,
    val price: Int,
    var count: Int,
    var total: Int
)

// 메뉴목록 (RecyclerView)의 어댑터 정의
class BasketAdapter(private var basketList: ArrayList<BasketItem>, private val mainListener: HomeFragment) : RecyclerView.Adapter<BasketAdapter.BasketHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BasketHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.basket_recycler_view, parent, false)
        return BasketHolder(view)
    }

    override fun onBindViewHolder(holder: BasketHolder, position: Int) {
        val item = basketList[position]
        holder.basketName.text = item.name
        holder.basketCount.text = item.count.toString()
        holder.basketTotal.text = item.total.toString()
        holder.itemView.setOnClickListener {
            // 여기서 각 item의 click function을 지정해주자.
            Toast.makeText(holder.itemView.context, "메뉴 삭제: ${item.name}", Toast.LENGTH_SHORT).show()
            mainListener.deleteFromBasket(item.id)
        }
    }

    override fun getItemCount(): Int {
        return basketList.size
    }

    inner class BasketHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val basketName: TextView = itemView.findViewById(R.id.basket_name)
        val basketCount: TextView = itemView.findViewById(R.id.basket_count)
        val basketTotal: TextView = itemView.findViewById(R.id.basket_price)
    }
}
