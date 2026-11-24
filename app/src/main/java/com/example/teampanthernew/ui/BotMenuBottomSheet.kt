package com.example.teampanthernew.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.teampanthernew.databinding.DialogBotMenuBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class BotMenuBottomSheet : BottomSheetDialogFragment() {

    private var _binding: DialogBotMenuBinding? = null
    private val binding get() = _binding!!

    var onReloadClickListener: (() -> Unit)? = null
    var onHomeClickListener: (() -> Unit)? = null
    var onBotSignalsClickListener: (() -> Unit)? = null
    var onLogoutClickListener: (() -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogBotMenuBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.menuReload.setOnClickListener {
            dismiss()
            onReloadClickListener?.invoke()
        }

        binding.menuHome.setOnClickListener {
            dismiss()
            onHomeClickListener?.invoke()
        }

        binding.menuBotSignals.setOnClickListener {
            dismiss()
            onBotSignalsClickListener?.invoke()
        }

        binding.menuLogout.setOnClickListener {
            dismiss()
            onLogoutClickListener?.invoke()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "BotMenuBottomSheet"
        fun newInstance() = BotMenuBottomSheet()
    }
}


