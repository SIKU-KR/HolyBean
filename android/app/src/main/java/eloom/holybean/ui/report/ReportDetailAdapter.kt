package eloom.holybean.ui.report

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import eloom.holybean.R
import eloom.holybean.data.model.ReportDetailItem

class ReportDetailAdapter : ListAdapter<ReportDetailItem, ReportDetailAdapter.ReportDetailHolder>(Diff) {

    object Diff : DiffUtil.ItemCallback<ReportDetailItem>() {
        override fun areItemsTheSame(oldItem: ReportDetailItem, newItem: ReportDetailItem): Boolean {
            return oldItem.name == newItem.name
        }

        override fun areContentsTheSame(oldItem: ReportDetailItem, newItem: ReportDetailItem): Boolean {
            return oldItem == newItem
        }
    }

    class ReportDetailHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReportDetailHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.list_item_basket, parent, false)
        return ReportDetailHolder(view)
    }

    override fun onBindViewHolder(holder: ReportDetailHolder, position: Int) {
        holder.bind(getItem(position))
    }
}
