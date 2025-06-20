package eloom.holybean.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import eloom.holybean.R
import eloom.holybean.data.model.MenuItem
import eloom.holybean.interfaces.HomeFunctions

// 메뉴목록 (RecyclerView)의 어댑터 정의
class MenuAdapter(private val itemList: ArrayList<MenuItem>, private val mainListner : HomeFunctions) : RecyclerView.Adapter<MenuAdapter.MenuViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MenuViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.list_item_menu, parent, false)
        return MenuViewHolder(view)
    }

    override fun onBindViewHolder(holder: MenuViewHolder, position: Int) {
        val item = itemList[position]
        holder.menuName.text = item.name
        holder.menuPrice.text = item.price.toString()
        holder.itemView.setOnClickListener {
            mainListner.addToBasket(item.id)
        }
    }

    override fun getItemCount(): Int {
        return itemList.size
    }

    fun updateList(newItemList: ArrayList<MenuItem>) {
        itemList.clear()
        itemList.addAll(newItemList)
        notifyDataSetChanged()
    }

    inner class MenuViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val menuName: TextView = itemView.findViewById(R.id.menu_name)
        val menuPrice: TextView = itemView.findViewById(R.id.menu_price)
    }
}