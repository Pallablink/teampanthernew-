package com.example.teampanthernew.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.example.teampanthernew.data.AuthPreferenceManager
import com.example.teampanthernew.databinding.DialogGameSettingsBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class GameSettingsBottomSheet : BottomSheetDialogFragment() {

    private var _binding: DialogGameSettingsBinding? = null
    private val binding get() = _binding!!

    var onClearCacheListener: (() -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogGameSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val prefManager = AuthPreferenceManager(requireContext())
        val userContact = when {
            prefManager.getUserPhone().isNotEmpty() -> prefManager.getUserPhone()
            prefManager.getUserEmail().isNotEmpty() -> prefManager.getUserEmail()
            prefManager.savedIdentifier.isNotEmpty() -> prefManager.savedIdentifier
            else -> "TeamPanther User"
        }

        binding.tvSettingUserContact.text = userContact
        binding.tvTargetUrlDisplay.text = prefManager.targetWebUrl

        binding.layoutClearCache.setOnClickListener {
            onClearCacheListener?.invoke()
            Toast.makeText(requireContext(), "Web cache & storage cleared!", Toast.LENGTH_SHORT).show()
            dismiss()
        }

        binding.layoutPlatformUrl.setOnClickListener {
            Toast.makeText(requireContext(), "Target: " + prefManager.targetWebUrl, Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "GameSettingsBottomSheet"
        fun newInstance() = GameSettingsBottomSheet()
    }
}


