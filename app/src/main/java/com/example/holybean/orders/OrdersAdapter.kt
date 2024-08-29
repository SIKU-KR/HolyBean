package com.example.holybean.orders

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.holybean.R
import com.example.holybean.orders.dto.OrderItem

class OrdersAdapter(private var ordersList: ArrayList<OrderItem>, private val ordersListener: OrdersFragmentFunction) : RecyclerView.Adapter<OrdersAdapter.OrdersHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OrdersHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.orders_recycler_view, parent, false)
        return OrdersHolder(view)
    }

    override fun onBindViewHolder(holder: OrdersHolder, position: Int) {
        val item = ordersList[position]
        holder.ordersId.text = "주문번호: ${item.orderId}"
        holder.ordersAmount.text = "주문금액: ${item.totalAmount}"
        holder.ordersMethod.text = "주문방식: ${item.method}"
        holder.ordersOrderer.text = "주문자: ${item.orderer}"
        holder.itemView.setOnClickListener {
            ordersListener.newOrderSelected(item.rowId, item.orderId, item.totalAmount)
        }
    }

    override fun getItemCount(): Int {
        return ordersList.size
    }

    inner class OrdersHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ordersId: TextView = itemView.findViewById(R.id.orders_id)
        val ordersAmount: TextView = itemView.findViewById(R.id.orders_amount)
        val ordersMethod: TextView = itemView.findViewById(R.id.orders_method)
        val ordersOrderer: TextView = itemView.findViewById(R.id.orders_orderer)
    }
}