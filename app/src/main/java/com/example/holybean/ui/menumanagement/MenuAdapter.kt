package com.example.holybean.ui.menumanagement

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.holybean.R
import com.example.holybean.data.model.MenuItem

class MenuAdapter(private val itemList: ArrayList<MenuItem>) : RecyclerView.Adapter<MenuAdapter.MenuViewHolder>(){
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MenuViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.list_item_menu_management, parent, false)
        return MenuViewHolder(view)
    }

    override fun onBindViewHolder(holder: MenuViewHolder, position: Int) {
        val item = itemList[position]
        holder.menuCode.text = "id:"+item.id.toString()
        holder.menuPlacement.text = "placement:"+item.order.toString()
        holder.menuName.text = item.name
        holder.menuPrice.text = item.price.toString()
        if(!item.inuse){
            holder.itemView.setBackgroundColor(holder.itemView.context.getColor(R.color.disabledGray))
        }
    }

    override fun getItemCount(): Int {
        return itemList.size
    }

    inner class MenuViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val menuCode: TextView = itemView.findViewById(R.id.menucode)
        val menuPlacement: TextView = itemView.findViewById(R.id.menuplacement)
        val menuName: TextView = itemView.findViewById(R.id.menuname)
        val menuPrice: TextView = itemView.findViewById(R.id.menuprice)
    }
}