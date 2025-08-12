package eloom.holybean.ui.orderlist

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import eloom.holybean.R
import eloom.holybean.data.model.OrdersDetailItem

class OrdersDetailAdapter : ListAdapter<OrdersDetailItem, OrdersDetailAdapter.OrdersDetailHolder>(OrdersDetailDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OrdersDetailHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.list_item_basket, parent, false)
        return OrdersDetailHolder(view)
    }

    override fun onBindViewHolder(holder: OrdersDetailHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item)
    }

    class OrdersDetailHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val basketName: TextView = itemView.findViewById(R.id.basket_name)
        private val basketCount: TextView = itemView.findViewById(R.id.basket_count)
        private val basketTotal: TextView = itemView.findViewById(R.id.basket_price)

        fun bind(item: OrdersDetailItem) {
            basketName.text = item.name
            basketCount.text = item.count.toString()
            basketTotal.text = item.subtotal.toString()
        }
    }

    private class OrdersDetailDiffCallback : DiffUtil.ItemCallback<OrdersDetailItem>() {
        override fun areItemsTheSame(oldItem: OrdersDetailItem, newItem: OrdersDetailItem): Boolean {
            return oldItem.name == newItem.name && oldItem.count == newItem.count && oldItem.subtotal == newItem.subtotal
        }

        override fun areContentsTheSame(oldItem: OrdersDetailItem, newItem: OrdersDetailItem): Boolean {
            return oldItem == newItem
        }
    }
}
