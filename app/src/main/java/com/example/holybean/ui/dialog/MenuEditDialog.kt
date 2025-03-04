package com.example.holybean.ui.dialog

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.example.holybean.interfaces.MainActivityListener
import com.example.holybean.data.model.MenuItem
import com.example.holybean.databinding.DialogMenuEditBinding
import com.example.holybean.ui.menumanagement.MenuManagementViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MenuEditDialog(private val item: MenuItem, private val mainListener: MainActivityListener?) : DialogFragment() {

    @Inject
    lateinit var service: MenuManagementViewModel

    private lateinit var binding: DialogMenuEditBinding

    private lateinit var menuNameEditText: EditText
    private lateinit var menuPriceEditText: EditText
    private lateinit var menuIdTextView: TextView
    private lateinit var menuOrderTextView: TextView

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(requireActivity())
        val inflater: LayoutInflater = requireActivity().layoutInflater
        binding = DialogMenuEditBinding.inflate(inflater)

        // 뷰 초기화
        menuNameEditText = binding.editMenuName
        menuPriceEditText = binding.editMenuPrice
        menuIdTextView = binding.editMenuid
        menuOrderTextView = binding.editMenuOrder

        // 기존 값 설정
        menuNameEditText.setText(item.name)
        menuPriceEditText.setText(item.price.toString())
        menuIdTextView.text = item.id.toString()
        menuOrderTextView.text = item.order.toString()

        // 버튼 설정
        val disableButton: Button = binding.disableEditButton
        val saveButton: Button = binding.saveEditButton
        val cancelButton: Button = binding.cancelEditButton

        if(!item.inuse){
            disableButton.text = "활성화"
        }

        disableButton.setOnClickListener {
            service.disableMenu(item)
            mainListener?.replaceMenuManagementFragment()
            dismiss()
        }

        saveButton.setOnClickListener {
            val newName = menuNameEditText.text.toString().trim()
            val newPrice = menuPriceEditText.text.toString().trim().toIntOrNull() ?: item.price
            val passwordDialog = PasswordDialog(requireContext()) {
                service.saveMenuChanges(item, newName, newPrice)
                mainListener?.replaceMenuManagementFragment()
                dismiss()
            }
            passwordDialog.show()
        }

        cancelButton.setOnClickListener {
            mainListener?.replaceMenuManagementFragment()
            dismiss()
        }

        builder.setView(binding.root)
        return builder.create()
    }
}
