package com.example.holybean.report

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.holybean.R
import com.example.holybean.dataclass.ReportDetailItem

class ReportDetailAdapter(private var detailData: ArrayList<ReportDetailItem>) : RecyclerView.Adapter<ReportDetailAdapter.ReportDetailHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReportDetailHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.basket_recycler_view, parent, false)
        return ReportDetailHolder(view)
    }

    override fun onBindViewHolder(holder: ReportDetailHolder, position: Int) {
        val item = detailData[position]
        holder.basketName.text = item.name
        holder.basketCount.text = item.quantity.toString()
        holder.basketTotal.text = item.subtotal.toString()
    }

    override fun getItemCount(): Int {
        return detailData.size
    }

    inner class ReportDetailHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val basketName: TextView = itemView.findViewById(R.id.basket_name)
        val basketCount: TextView = itemView.findViewById(R.id.basket_count)
        val basketTotal: TextView = itemView.findViewById(R.id.basket_price)
    }
}