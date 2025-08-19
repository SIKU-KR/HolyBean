package eloom.holybean.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import eloom.holybean.R
import eloom.holybean.data.model.CartItem

class CartAdapter(
    private val onClick: (CartItem) -> Unit
) : ListAdapter<CartItem, CartAdapter.BasketHolder>(Diff) {

    object Diff : DiffUtil.ItemCallback<CartItem>() {
        override fun areItemsTheSame(oldItem: CartItem, newItem: CartItem) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: CartItem, newItem: CartItem) = oldItem == newItem
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BasketHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.list_item_basket, parent, false)
        return BasketHolder(view)
    }

    override fun onBindViewHolder(holder: BasketHolder, position: Int) {
        holder.bind(getItem(position), onClick)
    }

    class BasketHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val basketName: TextView = itemView.findViewById(R.id.basket_name)
        private val basketCount: TextView = itemView.findViewById(R.id.basket_count)
        private val basketTotal: TextView = itemView.findViewById(R.id.basket_price)

        fun bind(item: CartItem, onClick: (CartItem) -> Unit) {
            basketName.text = item.name
            basketCount.text = item.count.toString()
            basketTotal.text = item.total.toString()
            itemView.setOnClickListener { onClick(item) }
        }
    }
}
