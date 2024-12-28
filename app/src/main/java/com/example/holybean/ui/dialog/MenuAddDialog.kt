package com.example.holybean.ui.dialog

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.example.holybean.interfaces.MainActivityListener
import com.example.holybean.data.model.MenuItem
import com.example.holybean.databinding.DialogMenuAddBinding
import com.example.holybean.ui.menumanagement.MenuManagementViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class MenuAddDialog(private val id: Int, private val placement: Int, private val mainListener: MainActivityListener?) : DialogFragment() {

    @Inject
    lateinit var service: MenuManagementViewModel

    private lateinit var binding: DialogMenuAddBinding

    private lateinit var menuNameEditText: EditText
    private lateinit var menuPriceEditText: EditText
    private lateinit var menuIdTextView: TextView
    private lateinit var menuOrderTextView: TextView

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = DialogMenuAddBinding.inflate(requireActivity().layoutInflater)

        initializeViews()
        setDefaultValues()
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
        menuIdTextView.text = id.toString()
        menuOrderTextView.text = placement.toString()
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
            validateAndSaveMenu(newName, newPrice)
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

    private fun validateAndSaveMenu(newName: String, newPrice: Int) {
        lifecycleScope.launch {
            val isNameValid = withContext(Dispatchers.IO) { service.isValidMenuName(newName) }
            if (!isNameValid) {
                Toast.makeText(requireContext(), "존재하는 메뉴입니다.", Toast.LENGTH_SHORT).show()
            } else {
                saveMenu(newName, newPrice)
            }
        }
    }

    private fun saveMenu(newName: String, newPrice: Int) {
        val item = MenuItem(id, newName, newPrice, placement, true)
        val passwordDialog = PasswordDialog(requireContext()) {
            service.addMenu(item)
            mainListener?.replaceMenuManagementFragment()
            dismiss()
        }
        passwordDialog.show()
    }

    private fun onCancelButtonClicked() {
        mainListener?.replaceMenuManagementFragment()
        dismiss()
    }
}
