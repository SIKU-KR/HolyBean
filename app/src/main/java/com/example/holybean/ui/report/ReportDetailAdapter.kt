package com.example.holybean.ui.report

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.holybean.R
import com.example.holybean.data.model.ReportDetailItem

class ReportDetailAdapter(
    private var detailData: MutableList<ReportDetailItem> = mutableListOf()
) : RecyclerView.Adapter<ReportDetailAdapter.ReportDetailHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReportDetailHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.list_item_basket, parent, false)
        return ReportDetailHolder(view)
    }

    override fun onBindViewHolder(holder: ReportDetailHolder, position: Int) {
        val item = detailData[position]
        holder.bind(item)
    }

    override fun getItemCount(): Int = detailData.size

    /**
     * Updates the adapter's data with a new list of [ReportDetailItem] and refreshes the RecyclerView.
     *
     * @param newDetailData The new list of [ReportDetailItem] to display.
     */
    fun updateData(newDetailData: List<ReportDetailItem>) {
        detailData.clear()
        detailData.addAll(newDetailData)
        notifyDataSetChanged()
    }

    inner class ReportDetailHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val basketName: TextView = itemView.findViewById(R.id.basket_name)
        private val basketCount: TextView = itemView.findViewById(R.id.basket_count)
        private val basketTotal: TextView = itemView.findViewById(R.id.basket_price)

        /**
         * Binds the [ReportDetailItem] data to the views.
         *
         * @param item The [ReportDetailItem] to bind.
         */
        fun bind(item: ReportDetailItem) {
            basketName.text = item.name
            basketCount.text = item.quantity.toString()
            basketTotal.text = item.subtotal.toString()
        }
    }
}
