package com.example.teampanthernew.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.example.teampanthernew.R
import com.example.teampanthernew.databinding.DialogForgotPasswordBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class ForgotPasswordBottomSheet : BottomSheetDialogFragment() {

    private var _binding: DialogForgotPasswordBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogForgotPasswordBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnSendReset.setOnClickListener {
            handleResetRequest()
        }
    }

    private fun handleResetRequest() {
        val target = binding.etResetTarget.text?.toString()?.trim() ?: ""

        if (target.isEmpty()) {
            binding.tilResetTarget.error = getString(R.string.error_empty_reset_target)
            return
        }

        val isEmail = Patterns.EMAIL_ADDRESS.matcher(target).matches()
        val isPhone = target.length >= 10 && target.all { it.isDigit() || it == '+' || it == '-' || it == ' ' }

        if (!isEmail && !isPhone) {
            binding.tilResetTarget.error = getString(R.string.hint_reset_target)
            return
        }

        binding.tilResetTarget.error = null
        binding.btnSendReset.isEnabled = false
        binding.pbResetLoading.visibility = View.VISIBLE

        // Simulate sending password reset instructions
        Handler(Looper.getMainLooper()).postDelayed({
            if (isAdded) {
                binding.pbResetLoading.visibility = View.GONE
                binding.btnSendReset.isEnabled = true
                Toast.makeText(
                    requireContext(),
                    getString(R.string.reset_sent_success),
                    Toast.LENGTH_LONG
                ).show()
                dismiss()
            }
        }, 1200)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "ForgotPasswordBottomSheet"
        fun newInstance() = ForgotPasswordBottomSheet()
    }
}


