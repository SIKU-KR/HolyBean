package eloom.holybean.ui.orderlist

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import eloom.holybean.R
import eloom.holybean.data.model.OrderItem

class OrdersAdapter(
    private val onOrderClick: (orderNumber: Int, totalAmount: Int) -> Unit
) : ListAdapter<OrderItem, OrdersAdapter.OrdersHolder>(OrderItemDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OrdersHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.list_item_orders, parent, false)
        return OrdersHolder(view)
    }

    override fun onBindViewHolder(holder: OrdersHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item, onOrderClick)
    }

    class OrdersHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ordersId: TextView = itemView.findViewById(R.id.orders_id)
        private val ordersAmount: TextView = itemView.findViewById(R.id.orders_amount)
        private val ordersMethod: TextView = itemView.findViewById(R.id.orders_method)
        private val ordersOrderer: TextView = itemView.findViewById(R.id.orders_orderer)

        fun bind(item: OrderItem, onOrderClick: (Int, Int) -> Unit) {
            ordersId.text = "주문번호: ${item.orderId}"
            ordersAmount.text = "주문금액: ${item.totalAmount}"
            ordersMethod.text = "주문방식: ${item.method}"
            ordersOrderer.text = "주문자: ${item.orderer}"

            itemView.setOnClickListener {
                onOrderClick(item.orderId, item.totalAmount)
            }
        }
    }

    private class OrderItemDiffCallback : DiffUtil.ItemCallback<OrderItem>() {
        override fun areItemsTheSame(oldItem: OrderItem, newItem: OrderItem): Boolean {
            return oldItem.orderId == newItem.orderId
        }

        override fun areContentsTheSame(oldItem: OrderItem, newItem: OrderItem): Boolean {
            return oldItem == newItem
        }
    }
}