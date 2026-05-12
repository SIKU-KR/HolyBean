package eloom.holybean.ui.credits

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import eloom.holybean.R
import eloom.holybean.data.model.CreditItem

class CreditsAdapter(
    private val onClick: (CreditItem) -> Unit
) : ListAdapter<CreditItem, CreditsAdapter.CreditsHolder>(Diff) {

    object Diff : DiffUtil.ItemCallback<CreditItem>() {
        override fun areItemsTheSame(oldItem: CreditItem, newItem: CreditItem): Boolean {
            return oldItem.orderId == newItem.orderId && oldItem.date == newItem.date
        }

        override fun areContentsTheSame(oldItem: CreditItem, newItem: CreditItem): Boolean {
            return oldItem == newItem
        }
    }

    class CreditsHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ordersId: TextView = itemView.findViewById(R.id.credits_id)
        private val ordersAmount: TextView = itemView.findViewById(R.id.credits_amount)
        private val ordersDate: TextView = itemView.findViewById(R.id.credits_date)
        private val ordersOrderer: TextView = itemView.findViewById(R.id.credits_orderer)

        fun bind(item: CreditItem, onClick: (CreditItem) -> Unit) {
            ordersId.text = "주문번호: ${item.orderId}"
            ordersAmount.text = "주문금액: ${item.totalAmount}"
            ordersDate.text = "주문일: ${item.date}"
            ordersOrderer.text = "주문자: ${item.orderer}"
            itemView.setOnClickListener { onClick(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CreditsHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.list_item_credits, parent, false)
        return CreditsHolder(view)
    }

    override fun onBindViewHolder(holder: CreditsHolder, position: Int) {
        holder.bind(getItem(position), onClick)
    }
}