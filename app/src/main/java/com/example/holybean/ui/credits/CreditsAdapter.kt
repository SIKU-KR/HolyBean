package com.example.holybean.ui.credits

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.holybean.R
import com.example.holybean.data.model.CreditItem
import com.example.holybean.interfaces.CreditsFragmentFunction

class CreditsAdapter(private var creditsList: ArrayList<CreditItem>, private val creditsListener: CreditsFragmentFunction) : RecyclerView.Adapter<CreditsAdapter.CreditsHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CreditsHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.list_item_credits, parent, false)
        return CreditsHolder(view)
    }

    override fun onBindViewHolder(holder: CreditsHolder, position: Int) {
        val item = creditsList[position]
        holder.ordersId.text = "주문번호: ${item.orderId}"
        holder.ordersAmount.text = "주문금액: ${item.totalAmount}"
        holder.ordersDate.text = "주문일: ${item.date}"
        holder.ordersOrderer.text = "주문자: ${item.orderer}"
        holder.itemView.setOnClickListener {
            creditsListener.newOrderSelected(item.rowId, item.orderId, item.totalAmount, item.date)
        }
    }

    override fun getItemCount(): Int {
        return creditsList.size
    }

    inner class CreditsHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ordersId: TextView = itemView.findViewById(R.id.credits_id)
        val ordersAmount: TextView = itemView.findViewById(R.id.credits_amount)
        val ordersDate: TextView = itemView.findViewById(R.id.credits_date)
        val ordersOrderer: TextView = itemView.findViewById(R.id.credits_orderer)
    }
}