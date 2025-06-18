package eloom.holybean.ui.orderlist

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import eloom.holybean.data.model.OrderItem
import eloom.holybean.data.model.OrdersDetailItem
import eloom.holybean.data.repository.LambdaRepository
import eloom.holybean.databinding.FragmentOrdersBinding
import eloom.holybean.interfaces.MainActivityListener
import eloom.holybean.interfaces.OrdersFragmentFunction
import eloom.holybean.ui.RvCustomDesign
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class OrdersFragment: Fragment(), OrdersFragmentFunction {

    @Inject
    lateinit var lambdaRepository: LambdaRepository
    private val viewModel: OrdersViewModel by viewModels()

    private lateinit var binding: FragmentOrdersBinding
    private lateinit var context: Context

    private var mainListener: MainActivityListener? = null

    private var orderNumber = 1

    private lateinit var orderNum: TextView
    private lateinit var totalPrice: TextView
    private lateinit var ordersBoard: RecyclerView
    private lateinit var ordersList: ArrayList<OrderItem>
    private lateinit var basket: RecyclerView
    private lateinit var basketList: ArrayList<OrdersDetailItem>
    private lateinit var ordersDetailAdapter: OrdersDetailAdapter // Declare here

    // 1. Lifecycle 관련 함수
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentOrdersBinding.inflate(inflater, container, false)
        val view = binding.root
        context = view.context

        orderNum = binding.orderNum
        totalPrice = binding.totalPriceNum
        initBasket()
        initOrderList()
        initViewOrderDetailButton()
        initReprintButton()
        initDeleteOrderButton()
        return view
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is MainActivityListener) {
            mainListener = context
        } else {
            throw RuntimeException("$context must implement OnFragmentInteractionListener")
        }
    }

    override fun onDetach() {
        super.onDetach()
        mainListener = null
    }

    // 2. UI 초기화 관련 함수
    private fun initOrderList() {
        ordersBoard = binding.orderBoard
        val boardAdapter = OrdersAdapter(ArrayList(), this@OrdersFragment) // Use an empty list initially
        ordersBoard.apply {
            adapter = boardAdapter
            layoutManager = GridLayoutManager(context, 1)
            addItemDecoration(RvCustomDesign(0, 0, 0, 20))
        }

        // Fetch data and update adapter
        lifecycleScope.launch {
            ordersList = lambdaRepository.getOrdersOfDay()
            boardAdapter.updateData(ordersList)
        }
    }

    private fun initBasket() {
        basketList = ArrayList()
        basket = binding.basket
        ordersDetailAdapter = OrdersDetailAdapter(basketList)
        basket.apply {
            adapter = ordersDetailAdapter
            layoutManager = GridLayoutManager(context, 1)
            addItemDecoration(RvCustomDesign(15, 15, 0, 0)) // 20dp의 여백
        }
    }

    private fun initViewOrderDetailButton() {
        binding.viewThisOrder.setOnClickListener {
            viewModel.fetchOrderDetail(orderNumber)
        }
        // LiveData 관찰
        viewModel.orderDetails.observe(viewLifecycleOwner) { fetchedBasketList ->
            ordersDetailAdapter.updateData(fetchedBasketList)
        }
    }

    private fun initReprintButton() {
        binding.reprint.setOnClickListener {
            if (this.basketList.isNotEmpty()) {
                viewModel.reprint(this.orderNumber, this.basketList)
            }
        }
    }

    private fun initDeleteOrderButton() {
        binding.deleteButton.setOnClickListener {
            if (this.basketList.isNotEmpty()) {
                val builder = AlertDialog.Builder(context)
                builder.setTitle("주문 내역을 삭제하시겠습니까?")
                    .setMessage("주문번호 ${this.orderNumber}번이 삭제되며 복구할 수 없습니다")
                    .setPositiveButton("확인") { _, _ ->
                        // Launch a coroutine to call the suspend function
                        lifecycleScope.launch {
                            try {
                                Toast.makeText(context, "주문 삭제 중...기다려주세요", Toast.LENGTH_SHORT).show()
                                val result = lambdaRepository.deleteOrder(viewModel.getCurrentDate(), orderNumber)
                                if (result == true) {
                                    Toast.makeText(context, "주문이 성공적으로 삭제되었습니다.", Toast.LENGTH_SHORT).show()
                                    mainListener?.replaceOrdersFragment()
                                } else {
                                    Toast.makeText(context, "주문 삭제에 실패했습니다.", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                                Toast.makeText(context, "오류가 발생했습니다. 다시 시도해주세요.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    .setNegativeButton("취소") { _, _ -> }
                    .show()
            } else {
                Toast.makeText(context, "주문 조회 후 클릭해주세요", Toast.LENGTH_SHORT).show()
            }
        }
    }


    // 3. 인터페이스 함수
    @SuppressLint("NotifyDataSetChanged")
    override fun newOrderSelected(num: Int, total: Int) {
        orderNumber = num
        orderNum.text = num.toString()
        totalPrice.text = total.toString()
        basketList.clear()
        basket.adapter?.notifyDataSetChanged()
    }
}
