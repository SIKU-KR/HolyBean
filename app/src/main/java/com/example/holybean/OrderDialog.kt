package com.example.holybean

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import androidx.fragment.app.DialogFragment

class OrderDialog : DialogFragment() {

    private lateinit var orderMethodGroup: RadioGroup
    private lateinit var orderNameEditText: EditText

    private var orderDialogListener: OrderDialogListener? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(requireActivity())
        val inflater = requireActivity().layoutInflater
        val view = inflater.inflate(R.layout.activity_process_order, null)

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
                else -> ""
            }
            // Enable orderNameEditText if "계좌이체" or "외상" is selected, disable otherwise
            orderNameEditText.isEnabled = selectedOrderMethod == "계좌이체" || selectedOrderMethod == "외상"
        }

        confirmButton.setOnClickListener {
            val selectedOrderMethod = when (orderMethodGroup.checkedRadioButtonId) {
                R.id.Button1 -> "현금"
                R.id.Button2 -> "쿠폰"
                R.id.Button3 -> "계좌이체"
                R.id.Button4 -> "외상"
                else -> ""
            }

            val ordererName = orderNameEditText.text.toString()

            // Handle the selected order method and order name
            handleOrder(selectedOrderMethod, ordererName)
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

    private fun handleOrder(orderMethod: String, ordererName: String) {
        orderDialogListener?.onOrderConfirmed(orderMethod, ordererName)
        // Handle the order here based on the selected order method and order name
        // You can perform the necessary actions like sending the order to the server or displaying a confirmation message.
    }
}
