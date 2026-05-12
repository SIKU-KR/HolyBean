package eloom.holybean.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import eloom.holybean.R
import eloom.holybean.data.model.MenuItem

class MenuAdapter(
    private val onClick: (MenuItem) -> Unit
) : ListAdapter<MenuItem, MenuAdapter.MenuViewHolder>(Diff) {

    object Diff : DiffUtil.ItemCallback<MenuItem>() {
        override fun areItemsTheSame(oldItem: MenuItem, newItem: MenuItem) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: MenuItem, newItem: MenuItem) = oldItem == newItem
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MenuViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.list_item_menu, parent, false)
        return MenuViewHolder(view)
    }

    override fun onBindViewHolder(holder: MenuViewHolder, position: Int) {
        holder.bind(getItem(position), onClick)
    }

    class MenuViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val menuName: TextView = itemView.findViewById(R.id.menu_name)
        private val menuPrice: TextView = itemView.findViewById(R.id.menu_price)

        fun bind(item: MenuItem, onClick: (MenuItem) -> Unit) {
            menuName.text = item.name
            menuPrice.text = item.price.toString()
            itemView.setOnClickListener { onClick(item) }
        }
    }
}