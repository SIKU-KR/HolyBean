package eloom.holybean.ui.menumanagement

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import eloom.holybean.R
import eloom.holybean.data.model.MenuItem

class MenuAdapter : ListAdapter<MenuItem, MenuAdapter.MenuViewHolder>(Diff) {

    object Diff : DiffUtil.ItemCallback<MenuItem>() {
        override fun areItemsTheSame(oldItem: MenuItem, newItem: MenuItem) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: MenuItem, newItem: MenuItem) = oldItem == newItem
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MenuViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.list_item_menu_management, parent, false)
        return MenuViewHolder(view)
    }

    override fun onBindViewHolder(holder: MenuViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    fun getItemAt(position: Int): MenuItem {
        return getItem(position)
    }

    class MenuViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val menuCode: TextView = itemView.findViewById(R.id.menucode)
        private val menuPlacement: TextView = itemView.findViewById(R.id.menuplacement)
        private val menuName: TextView = itemView.findViewById(R.id.menuname)
        private val menuPrice: TextView = itemView.findViewById(R.id.menuprice)

        fun bind(item: MenuItem) {
            menuCode.text = "id:" + item.id.toString()
            menuPlacement.text = "placement:" + item.order.toString()
            menuName.text = item.name
            menuPrice.text = item.price.toString()
            if (!item.inuse) {
                itemView.setBackgroundColor(itemView.context.getColor(R.color.disabledGray))
            } else {
                itemView.setBackgroundColor(itemView.context.getColor(android.R.color.white))
            }
        }
    }
}