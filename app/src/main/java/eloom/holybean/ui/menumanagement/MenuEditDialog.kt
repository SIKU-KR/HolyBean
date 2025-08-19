package eloom.holybean.ui.menumanagement

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import eloom.holybean.data.model.MenuItem
import eloom.holybean.databinding.DialogMenuEditBinding

@AndroidEntryPoint
class MenuEditDialog(private val item: MenuItem) : DialogFragment() {

    private val viewModel: MenuManagementViewModel by viewModels({ requireParentFragment() })
    private lateinit var binding: DialogMenuEditBinding

    private lateinit var menuNameEditText: EditText
    private lateinit var menuPriceEditText: EditText
    private lateinit var menuIdTextView: TextView
    private lateinit var menuOrderTextView: TextView

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = DialogMenuEditBinding.inflate(requireActivity().layoutInflater)

        initializeViews()
        setupViews()
        setupListeners()

        return AlertDialog.Builder(requireActivity())
            .setView(binding.root)
            .create()
    }

    private fun initializeViews() {
        menuNameEditText = binding.editMenuName
        menuPriceEditText = binding.editMenuPrice
        menuIdTextView = binding.editMenuid
        menuOrderTextView = binding.editMenuOrder
    }

    private fun setupViews() {
        menuNameEditText.setText(item.name)
        menuPriceEditText.setText(item.price.toString())
        menuIdTextView.text = item.id.toString()
        menuOrderTextView.text = item.order.toString()

        if (!item.inuse) {
            binding.disableEditButton.text = "활성화"
        }
    }

    private fun setupListeners() {
        binding.disableEditButton.setOnClickListener {
            val passwordDialog = PasswordDialog(requireContext()) {
                viewModel.toggleMenuInUse(item)
                dismiss()
            }
            passwordDialog.show()
        }

        binding.saveEditButton.setOnClickListener {
            val newName = menuNameEditText.text.toString().trim()
            val newPrice = menuPriceEditText.text.toString().trim().toIntOrNull() ?: item.price
            val passwordDialog = PasswordDialog(requireContext()) {
                viewModel.updateMenu(item, newName, newPrice)
                dismiss()
            }
            passwordDialog.show()
        }

        binding.cancelEditButton.setOnClickListener {
            dismiss()
        }
    }
}