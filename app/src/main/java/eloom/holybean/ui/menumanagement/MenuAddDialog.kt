package eloom.holybean.ui.menumanagement

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import eloom.holybean.databinding.DialogMenuAddBinding
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MenuAddDialog : DialogFragment() {

    private val viewModel: MenuManagementViewModel by viewModels({ requireParentFragment() })

    private lateinit var binding: DialogMenuAddBinding

    private lateinit var menuNameEditText: EditText
    private lateinit var menuPriceEditText: EditText
    private lateinit var menuIdTextView: TextView
    private lateinit var menuOrderTextView: TextView

    private var menuId: Int = 0
    private var menuPlacement: Int = 0

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = DialogMenuAddBinding.inflate(requireActivity().layoutInflater)

        initializeViews()

        lifecycleScope.launch {
            menuId = viewModel.getNextAvailableId()
            menuPlacement = viewModel.getNextAvailablePlacement()
            setDefaultValues()
        }

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

    private fun setDefaultValues() {
        menuIdTextView.text = menuId.toString()
        menuOrderTextView.text = menuPlacement.toString()
    }

    private fun setupListeners() {
        binding.saveEditButton.setOnClickListener { onSaveButtonClicked() }
        binding.cancelEditButton.setOnClickListener { onCancelButtonClicked() }
    }

    private fun onSaveButtonClicked() {
        val newName = menuNameEditText.text.toString().trim()
        val newPriceText = menuPriceEditText.text.toString().trim()

        if (validateInput(newName, newPriceText)) {
            val newPrice = newPriceText.toInt()
            val action = {
                viewModel.addMenu(menuId, newName, newPrice, menuPlacement)
                dismiss()
            }
            if (viewModel.isPasswordSessionVerified()) {
                action()
            } else {
                PasswordDialog(requireContext()) {
                    viewModel.markPasswordSessionAsVerified()
                    action()
                }.show()
            }
        }
    }

    private fun validateInput(newName: String, newPriceText: String): Boolean {
        return when {
            newName.isEmpty() -> {
                Toast.makeText(requireContext(), "메뉴 이름을 입력해주세요.", Toast.LENGTH_SHORT).show()
                false
            }

            newPriceText.isEmpty() -> {
                Toast.makeText(requireContext(), "가격을 입력해주세요.", Toast.LENGTH_SHORT).show()
                false
            }

            newPriceText.toIntOrNull() == null || newPriceText.toInt() < 0 -> {
                Toast.makeText(requireContext(), "유효한 가격을 입력해주세요.", Toast.LENGTH_SHORT).show()
                false
            }

            else -> true
        }
    }

    private fun onCancelButtonClicked() {
        dismiss()
    }
}