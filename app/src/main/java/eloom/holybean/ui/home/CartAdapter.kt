package eloom.holybean.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import eloom.holybean.R
import eloom.holybean.data.model.CartItem

// 메뉴목록 (RecyclerView)의 어댑터 정의
class CartAdapter(private var basketList: ArrayList<CartItem>, private val mainListener: HomeFragment) : RecyclerView.Adapter<CartAdapter.BasketHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BasketHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.list_item_basket, parent, false)
        return BasketHolder(view)
    }

    override fun onBindViewHolder(holder: BasketHolder, position: Int) {
        val item = basketList[position]
        holder.basketName.text = item.name
        holder.basketCount.text = item.count.toString()
        holder.basketTotal.text = item.total.toString()
        holder.itemView.setOnClickListener {
            // 여기서 각 item의 click function을 지정해주자.
            // Toast.makeText(holder.itemView.context, "메뉴 삭제: ${item.name}", Toast.LENGTH_SHORT).show()
            mainListener.deleteFromBasket(item.id)
        }
    }

    override fun getItemCount(): Int {
        return basketList.size
    }

    inner class BasketHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val basketName: TextView = itemView.findViewById(R.id.basket_name)
        val basketCount: TextView = itemView.findViewById(R.id.basket_count)
        val basketTotal: TextView = itemView.findViewById(R.id.basket_price)
    }
}
