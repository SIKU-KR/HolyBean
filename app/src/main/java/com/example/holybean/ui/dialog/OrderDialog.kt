package com.example.holybean.ui.dialog

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.text.InputType
import android.text.TextUtils
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.example.holybean.R
import com.example.holybean.data.model.*
import com.example.holybean.interfaces.OrderDialogListener
import java.lang.Exception
import java.util.UUID

class OrderDialog(val data: OrderDialogData) : DialogFragment() {
    private var orderDialogListener: OrderDialogListener? = null

    // view components
    private lateinit var takeOptionGroup: RadioGroup
    private lateinit var orderMethodGroup: RadioGroup
    private lateinit var extraMethodGroup: RadioGroup
    private lateinit var extraMethodCB: CheckBox
    private lateinit var extraMethodTitle: TextView
    private lateinit var ordererInputTitle: TextView
    private lateinit var extraMethodAmount: EditText
    private lateinit var orderNameEditText: EditText
    private lateinit var confirmButton: Button
    private lateinit var cancelButton: Button

    private lateinit var extraMethodInput: LinearLayout

    private var cbCondition = false
    private var totalPrice = 0
    private var secondAmount = 0

    companion object {
        fun newInstance(data: OrderDialogData): OrderDialog {
            val fragment = OrderDialog(data)
            val args = Bundle()
            args.putInt("amount", data.totalPrice)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(requireActivity())
        val inflater = requireActivity().layoutInflater
        val view = inflater.inflate(R.layout.dialog_process_order, null)

        bindView(view)

        totalPrice = arguments?.getInt("amount", 0) ?: 0

        // 무료제공 버튼 클릭 시에 듀얼 메소드 옵션 비활성화
        orderMethodGroup.setOnCheckedChangeListener { group, checkedId ->
            val selectedOrderMethod = getFirstOption()
            if (selectedOrderMethod == "무료제공") {
                deactivateDualOption()
                extraMethodCB.isChecked = false
                extraMethodCB.isEnabled = false
            } else {
                extraMethodCB.isEnabled = true
            }
        }

        extraMethodGroup.setOnCheckedChangeListener { group, checkedId ->
            val selectedOrderMethod = getSecondOption()
            extraMethodTitle.text = "$selectedOrderMethod 결제금액"
        }

        confirmButton.setOnClickListener {
            if (!cbCondition) processSingleMethod()
            else processDualMethod()
        }

        cancelButton.setOnClickListener {
            dismiss()
        }

        extraMethodCB.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) {
                activateDualOption()
            } else {
                deactivateDualOption()
            }
        }

        builder.setView(view)
        return builder.create()
    }

    private fun bindView(view: View) {
        takeOptionGroup = view.findViewById(R.id.takeOptionGroup)
        orderMethodGroup = view.findViewById(R.id.orderMethodGroup)
        extraMethodGroup = view.findViewById(R.id.dualMethodGroup)
        extraMethodCB = view.findViewById(R.id.dualMethodCB)
        extraMethodTitle = view.findViewById(R.id.dualMethodAmountTitle)
        extraMethodAmount = view.findViewById(R.id.dualMethodAmount)
        extraMethodInput = view.findViewById(R.id.dualMethodInputGroup)
        orderNameEditText = view.findViewById(R.id.orderNameEditText)
        ordererInputTitle = view.findViewById(R.id.orderNameTitle)
        confirmButton = view.findViewById(R.id.confirmButton)
        cancelButton = view.findViewById(R.id.cancelButton)
        extraMethodAmount.inputType = InputType.TYPE_CLASS_NUMBER
    }

    fun setOrderDialogListener(listener: OrderDialogListener) {
        orderDialogListener = listener
    }

    private fun activateDualOption() {
        cbCondition = true
        extraMethodGroup.visibility = View.VISIBLE
        extraMethodInput.visibility = View.VISIBLE
    }

    private fun deactivateDualOption() {
        cbCondition = false
        extraMethodGroup.visibility = View.GONE
        extraMethodInput.visibility = View.GONE
    }

    private fun checkMethodIfOrdererNeeded(input: String): Boolean {
        return input == "계좌이체" || input == "외상"
    }

    private fun checkMethodIfOrdererNeeded(input1: String, input2: String): Boolean {
        return input1 == "계좌이체" || input1 == "외상" || input2 == "계좌이체" || input2 == "외상"
    }

    private fun getFirstOption(): String {
        val value = when (orderMethodGroup.checkedRadioButtonId) {
            R.id.Button1 -> "현금"
            R.id.Button2 -> "쿠폰"
            R.id.Button3 -> "계좌이체"
            R.id.Button4 -> "외상"
            R.id.Button5 -> "무료제공"
            else -> ""
        }
        return value
    }

    private fun getSecondOption(): String {
        val value = when (extraMethodGroup.checkedRadioButtonId) {
            R.id.extraButton1 -> "현금"
            R.id.extraButton2 -> "쿠폰"
            R.id.extraButton3 -> "계좌이체"
            R.id.extraButton4 -> "외상"
            else -> ""
        }
        return value
    }

    private fun getCredit(option: String): Int{
        if(option.equals("외상")){
            return 1
        }
        return 0;
    }

    private fun getCredit(option1: String, option2: String): Int{
        if(option1.equals("외상") || option2.equals("외상")){
            return 1
        }
        return 0;
    }

    private fun showToastText(text: String) {
        Toast.makeText(requireActivity(), text, Toast.LENGTH_SHORT).show()
    }

    private fun processSingleMethod() {
        val selectedTakeOption = getTakeOption()
        val selectedOrderMethod = getFirstOption()
        if (checkMethodIfOrdererNeeded(selectedOrderMethod) && TextUtils.isEmpty(orderNameEditText.text.toString())) {
            showToastText("주문자를 입력하세요")
            return
        }
        val methodList = listOf(PaymentMethod(selectedOrderMethod, data.totalPrice))
        val ordererName = orderNameEditText.text.toString()

        orderDialogListener?.onOrderConfirmed(
            Order(
                data.date,
                data.orderNum,
                getCredit(selectedOrderMethod),
                ordererName,
                data.cartItems,
                methodList,
                data.totalPrice
            ), selectedTakeOption
        )

//        orderDialogListener?.onOrderConfirmed(
//            OrderData(
//                data.cartItems,
//                data.orderNum,
//                data.totalPrice,
//                selectedTakeOption,
//                ordererName,
//                selectedOrderMethod,
//                data.date,
//                UUID.randomUUID().toString()
//            )
//        )
        dismiss()
    }

    private fun processDualMethod() {
        val selectedTakeOption = getTakeOption()
        val selectedOrderMethod = getFirstOption()
        val selectedExtraMethod = getSecondOption()

        try {
            secondAmount = Integer.parseInt(extraMethodAmount.text.toString())
        } catch (e: Exception) {
            showToastText("올바른 결제금액이 아닙니다")
            return
        }

        if (checkMethodIfOrdererNeeded(selectedOrderMethod, selectedExtraMethod)
            && TextUtils.isEmpty(orderNameEditText.getText().toString())
        ) {
            showToastText("주문자를 입력하세요")
            return
        }

        if (TextUtils.isEmpty(extraMethodAmount.getText().toString())) {
            showToastText("분할 결제 금액을 입력하세요")
            return
        }

        if (totalPrice - secondAmount <= 0) {
            showToastText("분할 결제 금액을 확인하세요")
            return
        }

        val ordererName = orderNameEditText.text.toString()

        val methodList = listOf(
            PaymentMethod(selectedOrderMethod, totalPrice - secondAmount),
            PaymentMethod(selectedExtraMethod, secondAmount)
        )
        // 결제금액 확인
        val builder = AlertDialog.Builder(context)
        builder.setTitle("결제금액 확인")
            .setMessage("$selectedOrderMethod : ${totalPrice - secondAmount}원, $selectedExtraMethod : ${secondAmount}원")
            .setPositiveButton("확인") { _, _ ->
                orderDialogListener?.onOrderConfirmed(
                    Order(
                        data.date,
                        data.orderNum,
                        getCredit(selectedOrderMethod, selectedExtraMethod),
                        ordererName,
                        data.cartItems,
                        methodList,
                        data.totalPrice
                    ),
                    selectedTakeOption
                )
//                orderDialogListener?.onOrderConfirmed(
//                    OrderDataWithDualMethod(
//                        data.cartItems,
//                        data.orderNum,
//                        data.totalPrice,
//                        selectedTakeOption,
//                        ordererName,
//                        selectedOrderMethod,
//                        selectedExtraMethod,
//                        totalPrice - secondAmount,
//                        secondAmount,
//                        data.date
//                    )
//                )
                dismiss()
            }.setNegativeButton("취소") { _, _ -> }.show()
    }

    private fun getTakeOption(): String {
        val value = when (takeOptionGroup.checkedRadioButtonId) {
            R.id.togo -> "일회용컵"
            R.id.eatin -> "머그컵"
            else -> ""
        }
        return value
    }


}
