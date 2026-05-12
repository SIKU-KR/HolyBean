package eloom.holybean.ui.credits

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
import eloom.holybean.databinding.FragmentCreditBinding
import eloom.holybean.interfaces.MainActivityListener
import eloom.holybean.ui.RvCustomDesign
import eloom.holybean.ui.orderlist.OrdersDetailAdapter
import kotlinx.coroutines.launch

@AndroidEntryPoint
class CreditsFragment : Fragment() {

    private val viewModel: CreditsViewModel by viewModels()

    private var _binding: FragmentCreditBinding? = null
    private val binding get() = _binding!!

    private var mainListener: MainActivityListener? = null

    private lateinit var creditsAdapter: CreditsAdapter
    private lateinit var ordersDetailAdapter: OrdersDetailAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCreditBinding.inflate(inflater, container, false)
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
        creditsAdapter = CreditsAdapter { creditItem ->
            viewModel.selectOrder(creditItem.orderId, creditItem.totalAmount, creditItem.date)
        }

        ordersDetailAdapter = OrdersDetailAdapter()
    }

    private fun setupRecyclerViews() {
        binding.orderBoard.apply {
            adapter = creditsAdapter
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
            viewModel.fetchOrderDetail()
        }

        binding.deleteCredit.setOnClickListener {
            viewModel.handleDeleteButton()
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

    private fun updateUI(uiState: CreditsViewModel.CreditsUiState) {
        // Update credits list
        creditsAdapter.submitList(uiState.creditsList)

        // Update selected order info
        binding.orderNum.text = uiState.selectedOrderNumber.toString()
        binding.totalPriceNum.text = uiState.selectedOrderTotal.toString()

        // Update order details
        ordersDetailAdapter.submitList(uiState.orderDetails)
    }

    private fun handleUiEvent(event: CreditsViewModel.CreditsUiEvent) {
        when (event) {
            is CreditsViewModel.CreditsUiEvent.ShowToast -> {
                showToastMessage(event.message)
            }

            is CreditsViewModel.CreditsUiEvent.RefreshCredits -> {
                mainListener?.replaceCreditsFragment()
            }
        }
    }

    private fun showToastMessage(msg: String) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }
}
