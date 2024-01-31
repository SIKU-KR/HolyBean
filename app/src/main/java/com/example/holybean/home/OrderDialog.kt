package com.example.holybean.home

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import androidx.fragment.app.DialogFragment
import com.example.holybean.R

class OrderDialog : DialogFragment() {

    private lateinit var takeOptionGroup: RadioGroup
    private lateinit var orderMethodGroup: RadioGroup
    private lateinit var orderNameEditText: EditText

    private var orderDialogListener: OrderDialogListener? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(requireActivity())
        val inflater = requireActivity().layoutInflater
        val view = inflater.inflate(R.layout.activity_process_order, null)

        takeOptionGroup = view.findViewById(R.id.takeOptionGroup)
        orderMethodGroup = view.findViewById(R.id.orderMethodGroup)
        orderNameEditText = view.findViewById(R.id.orderNameEditText)

        val confirmButton = view.findViewById<Button>(R.id.confirmButton)
        val cancelButton = view.findViewById<Button>(R.id.cancelButton)

        orderMethodGroup.setOnCheckedChangeListener { group, checkedId ->
            val selectedOrderMethod = when (checkedId) {
                R.id.Button1 -> "현금"
                R.id.Button2 -> "쿠폰"
                R.id.Button3 -> "계좌이체"
                R.id.Button4 -> "외상"
                R.id.Button5 -> "무료제공"
                else -> ""
            }
            // Enable orderNameEditText if "계좌이체" or "외상" is selected, disable otherwise
            orderNameEditText.isEnabled = selectedOrderMethod == "계좌이체" || selectedOrderMethod == "외상" || selectedOrderMethod == "무료제공"
        }

        confirmButton.setOnClickListener {
            val selectedTakeOption = when (takeOptionGroup.checkedRadioButtonId) {
                R.id.togo -> "일회용컵"
                R.id.eatin -> "머그컵"
                else -> ""
            }

            val selectedOrderMethod = when (orderMethodGroup.checkedRadioButtonId) {
                R.id.Button1 -> "현금"
                R.id.Button2 -> "쿠폰"
                R.id.Button3 -> "계좌이체"
                R.id.Button4 -> "외상"
                R.id.Button5 -> "무료제공"
                else -> ""
            }

            val ordererName = orderNameEditText.text.toString()

            // Handle the selected order method and order name
            handleOrder(selectedOrderMethod, ordererName, selectedTakeOption)
            dismiss()
        }

        cancelButton.setOnClickListener {
            dismiss()
        }

        builder.setView(view)
        return builder.create()
    }

    fun setOrderDialogListener(listener: OrderDialogListener){
        orderDialogListener = listener
    }

    private fun handleOrder(orderMethod: String, ordererName: String, takeOption: String) {
        orderDialogListener?.onOrderConfirmed(orderMethod, ordererName, takeOption)
        // Handle the order here based on the selected order method and order name
        // You can perform the necessary actions like sending the order to the server or displaying a confirmation message.
    }
}
