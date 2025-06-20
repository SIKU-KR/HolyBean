package eloom.holybean.ui.report

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import eloom.holybean.databinding.FragmentReportBinding
import dagger.hilt.android.AndroidEntryPoint
import java.text.NumberFormat
import java.util.*

@AndroidEntryPoint
class ReportFragment : Fragment() {

    private var _binding: FragmentReportBinding? = null
    private val binding get() = _binding!!

    // Initialize ViewModel
    private val viewModel: ReportViewModel by viewModels()

    // Adapter for RecyclerView
    private lateinit var reportDetailAdapter: ReportDetailAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentReportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        initializeDefaultDates()
        setupObservers()
        setupListeners()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupRecyclerView() {
        reportDetailAdapter = ReportDetailAdapter()
        binding.reportDetail.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = reportDetailAdapter
        }
    }

    private fun initializeDefaultDates() {
        val today =
            java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT))
        binding.date1Text.text = today
        binding.date2Text.text = today
    }

    /**
     * Sets up observers for LiveData from the ViewModel.
     */
    private fun setupObservers() {
        // Observe report summary data
        viewModel.reportData.observe(viewLifecycleOwner) { data ->
            updateReportSummary(data)
        }

        // Observe report detail data
        viewModel.reportDetailData.observe(viewLifecycleOwner) { details ->
            reportDetailAdapter.updateData(details)
        }

        // Observe report title
        viewModel.reportTitle.observe(viewLifecycleOwner) { title ->
            binding.reportTitle.text = title
        }

        // Observe error messages
        viewModel.errorMessage.observe(viewLifecycleOwner) { message ->
            if (message.isNotEmpty()) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupListeners() {
        binding.date1Button.setOnClickListener {
            showDatePicker { selectedDate ->
                binding.date1Text.text = selectedDate
            }
        }

        binding.date2Button.setOnClickListener {
            showDatePicker { selectedDate ->
                binding.date2Text.text = selectedDate
            }
        }

        binding.printButton.setOnClickListener {
            viewModel.printReport()
        }

        binding.loadButton.setOnClickListener {
            handleLoadAction()
        }
    }

    private fun handleLoadAction() {
        val startDate = binding.date1Text.text.toString()
        val endDate = binding.date2Text.text.toString()
        viewModel.loadReportData(startDate, endDate)
    }

    private fun showDatePicker(onDateSelected: (String) -> Unit) {
        val calendar = Calendar.getInstance()
        DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                val formattedDate = viewModel.formatDate(year, month, dayOfMonth)
                onDateSelected(formattedDate)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun updateReportSummary(data: Map<String, Int>) {
        val numberFormatter = NumberFormat.getInstance(Locale.getDefault())

        binding.apply {
            totalSell.text = "총 판매금액 : ${numberFormatter.format(data["총합"] ?: 0)}"
            cashSell.text = "현금 판매금액 : ${numberFormatter.format(data["현금"] ?: 0)}"
            couponSell.text = "쿠폰 판매금액 : ${numberFormatter.format(data["쿠폰"] ?: 0)}"
            transferSell.text = "계좌이체 판매금액 : ${numberFormatter.format(data["계좌이체"] ?: 0)}"
            creditSell.text = "외상 판매금액 : ${numberFormatter.format(data["외상"] ?: 0)}"
            freelyOut.text = "무료제공 금액 : ${numberFormatter.format(data["무료제공"] ?: 0)}"
            freeCouponSell.text = "무료쿠폰 금액 : ${numberFormatter.format(data["무료쿠폰"] ?: 0)}"
        }
    }
}
