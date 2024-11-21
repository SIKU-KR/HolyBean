package com.example.holybean.ui.orderlist

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.holybean.R
import com.example.holybean.data.model.OrdersDetailItem

class OrdersDetailAdapter(private var basketList: ArrayList<OrdersDetailItem>) : RecyclerView.Adapter<OrdersDetailAdapter.OrdersDetailHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OrdersDetailHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.list_item_basket, parent, false)
        return OrdersDetailHolder(view)
    }

    override fun onBindViewHolder(holder: OrdersDetailHolder, position: Int) {
        val item = basketList[position]
        holder.basketName.text = item.name
        holder.basketCount.text = item.count.toString()
        holder.basketTotal.text = item.subtotal.toString()
    }

    override fun getItemCount(): Int {
        return basketList.size
    }

    inner class OrdersDetailHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val basketName: TextView = itemView.findViewById(R.id.basket_name)
        val basketCount: TextView = itemView.findViewById(R.id.basket_count)
        val basketTotal: TextView = itemView.findViewById(R.id.basket_price)
    }
}
