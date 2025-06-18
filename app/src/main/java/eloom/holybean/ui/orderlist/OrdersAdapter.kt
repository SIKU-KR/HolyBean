package eloom.holybean.ui.orderlist

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import eloom.holybean.R
import eloom.holybean.interfaces.OrdersFragmentFunction
import eloom.holybean.data.model.OrderItem

class OrdersAdapter(private var ordersList: ArrayList<OrderItem>, private val ordersListener: OrdersFragmentFunction) : RecyclerView.Adapter<OrdersAdapter.OrdersHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OrdersHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.list_item_orders, parent, false)
        return OrdersHolder(view)
    }

    override fun onBindViewHolder(holder: OrdersHolder, position: Int) {
        val item = ordersList[position]
        holder.ordersId.text = "주문번호: ${item.orderId}"
        holder.ordersAmount.text = "주문금액: ${item.totalAmount}"
        holder.ordersMethod.text = "주문방식: ${item.method}"
        holder.ordersOrderer.text = "주문자: ${item.orderer}"
        holder.itemView.setOnClickListener {
            ordersListener.newOrderSelected(item.orderId, item.totalAmount)
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

    fun updateData(newOrders: List<OrderItem>) {
        ordersList.clear() // 기존 데이터 초기화
        ordersList.addAll(newOrders) // 새로운 데이터 추가
        notifyDataSetChanged() // RecyclerView 갱신
    }
}