package eloom.holybean.ui.orderlist

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import eloom.holybean.databinding.FragmentOrdersBinding
import eloom.holybean.interfaces.MainActivityListener
import eloom.holybean.ui.RvCustomDesign
import kotlinx.coroutines.launch

@AndroidEntryPoint
class OrdersFragment : Fragment() {

    private val viewModel: OrdersViewModel by viewModels()

    private var _binding: FragmentOrdersBinding? = null
    private val binding get() = _binding!!

    private var mainListener: MainActivityListener? = null

    private lateinit var ordersAdapter: OrdersAdapter
    private lateinit var ordersDetailAdapter: OrdersDetailAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOrdersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupAdapters()
        setupRecyclerViews()
        setupButtons()
        observeViewModel()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is MainActivityListener) {
            mainListener = context
        } else {
            throw RuntimeException("$context must implement MainActivityListener")
        }
    }

    override fun onDetach() {
        super.onDetach()
        mainListener = null
    }

    private fun setupAdapters() {
        ordersAdapter = OrdersAdapter { orderNumber, totalAmount ->
            viewModel.selectOrder(orderNumber, totalAmount)
        }

        ordersDetailAdapter = OrdersDetailAdapter()
    }

    private fun setupRecyclerViews() {
        binding.orderBoard.apply {
            adapter = ordersAdapter
            layoutManager = GridLayoutManager(requireContext(), 1)
            addItemDecoration(RvCustomDesign(0, 0, 0, 20))
        }

        binding.basket.apply {
            adapter = ordersDetailAdapter
            layoutManager = GridLayoutManager(requireContext(), 1)
            addItemDecoration(RvCustomDesign(15, 15, 0, 0))
        }
    }

    private fun setupButtons() {
        binding.viewThisOrder.setOnClickListener {
            val currentState = viewModel.uiState.value
            viewModel.fetchOrderDetail(currentState.selectedOrderNumber)
        }

        binding.reprint.setOnClickListener {
            viewModel.reprint()
        }

        binding.deleteButton.setOnClickListener {
            showDeleteConfirmationDialog()
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { uiState ->
                        updateUI(uiState)
                    }
                }

                launch {
                    viewModel.uiEvent.collect { event ->
                        handleUiEvent(event)
                    }
                }
            }
        }
    }

    private fun updateUI(uiState: OrdersViewModel.OrdersUiState) {
        // Update orders list
        ordersAdapter.submitList(uiState.ordersList)

        // Update selected order info
        binding.orderNum.text = uiState.selectedOrderNumber.toString()
        binding.totalPriceNum.text = uiState.selectedOrderTotal.toString()

        // Update order details
        ordersDetailAdapter.submitList(uiState.orderDetails)

        // Handle delete status
        when (uiState.deleteStatus) {
            is OrdersViewModel.DeleteStatus.Loading -> {
                showToastMessage("주문 삭제 중...기다려주세요")
            }

            is OrdersViewModel.DeleteStatus.Success -> {
                showToastMessage("주문이 성공적으로 삭제되었습니다.")
                mainListener?.replaceOrdersFragment()
                viewModel.resetDeleteStatus()
            }

            is OrdersViewModel.DeleteStatus.Error -> {
                showToastMessage(uiState.deleteStatus.message)
                viewModel.resetDeleteStatus()
            }

            is OrdersViewModel.DeleteStatus.Idle -> {
                // Do nothing
            }
        }
    }

    private fun handleUiEvent(event: OrdersViewModel.OrdersUiEvent) {
        when (event) {
            is OrdersViewModel.OrdersUiEvent.ShowToast -> {
                showToastMessage(event.message)
            }

            is OrdersViewModel.OrdersUiEvent.RefreshOrders -> {
                mainListener?.replaceOrdersFragment()
            }
        }
    }

    private fun showDeleteConfirmationDialog() {
        val currentState = viewModel.uiState.value
        AlertDialog.Builder(requireContext())
            .setTitle("주문 내역을 삭제하시겠습니까?")
            .setMessage("주문번호 ${currentState.selectedOrderNumber}번이 삭제되며 복구할 수 없습니다")
            .setPositiveButton("확인") { _, _ ->
                viewModel.deleteOrder()
            }
            .setNegativeButton("취소") { _, _ -> }
            .show()
    }

    private fun showToastMessage(msg: String) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }
}
