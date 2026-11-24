package com.example.teampanthernew.ui

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.teampanthernew.R
import com.example.teampanthernew.data.AuthPreferenceManager
import com.example.teampanthernew.databinding.BottomSheetUserProfileBinding
import com.example.teampanthernew.utils.Tilt3DHelper
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class UserProfileBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetUserProfileBinding? = null
    private val binding get() = _binding!!

    private lateinit var prefManager: AuthPreferenceManager

    // Gallery Image Picker Contract
    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            prefManager.userAvatarUri = uri.toString()
            prefManager.userAvatarType = "CUSTOM"
            updateAvatarDisplay()
            Toast.makeText(requireContext(), "Profile avatar updated from gallery!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun getTheme(): Int = R.style.BottomSheetDialogTheme

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetUserProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefManager = AuthPreferenceManager(requireContext())

        setupUserData()
        setupAvatarSelection()
        setupActions()
    }

    private fun setupUserData() {
        val phone = prefManager.getUserPhone().ifEmpty { "+91 98765 43210" }
        val email = prefManager.getUserEmail().ifEmpty { "user@teampanther.com" }
        val status = prefManager.userStatus.lowercase()

        binding.tvProfilePhone.text = phone
        binding.tvProfileEmail.text = email

        // Account status from Admin Panel: active, inactive, banned, suspended
        when (status) {
            "active" -> {
                binding.tvUserStatusBadge.text = "ACTIVE"
                binding.tvUserStatusBadge.setBackgroundResource(R.drawable.bg_history_w)
                binding.tvUserStatusBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.vip_white))
            }
            "inactive" -> {
                binding.tvUserStatusBadge.text = "INACTIVE"
                binding.tvUserStatusBadge.setBackgroundResource(R.drawable.bg_history_s)
                binding.tvUserStatusBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.vip_white))
            }
            "banned" -> {
                binding.tvUserStatusBadge.text = "BANNED"
                binding.tvUserStatusBadge.setBackgroundResource(R.drawable.bg_history_l)
                binding.tvUserStatusBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.vip_white))
            }
            "suspended" -> {
                binding.tvUserStatusBadge.text = "SUSPENDED"
                binding.tvUserStatusBadge.setBackgroundResource(R.drawable.bg_history_b)
                binding.tvUserStatusBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.vip_white))
            }
            else -> {
                binding.tvUserStatusBadge.text = status.uppercase()
                binding.tvUserStatusBadge.setBackgroundResource(R.drawable.bg_history_w)
            }
        }

        updateAvatarDisplay()
    }

    private fun setupAvatarSelection() {
        // Preset Avatars
        binding.ivPresetTiger.setOnClickListener {
            prefManager.userAvatarType = "TIGER"
            prefManager.userAvatarUri = null
            updateAvatarDisplay()
            Toast.makeText(requireContext(), "Tiger Avatar Selected", Toast.LENGTH_SHORT).show()
        }

        binding.ivPresetRobot.setOnClickListener {
            prefManager.userAvatarType = "ROBOT"
            prefManager.userAvatarUri = null
            updateAvatarDisplay()
            Toast.makeText(requireContext(), "VIP Bot Avatar Selected", Toast.LENGTH_SHORT).show()
        }

        binding.ivPresetCrown.setOnClickListener {
            prefManager.userAvatarType = "CROWN"
            prefManager.userAvatarUri = null
            updateAvatarDisplay()
            Toast.makeText(requireContext(), "Royal Crown Avatar Selected", Toast.LENGTH_SHORT).show()
        }

        binding.ivPresetShield.setOnClickListener {
            prefManager.userAvatarType = "SHIELD"
            prefManager.userAvatarUri = null
            updateAvatarDisplay()
            Toast.makeText(requireContext(), "Neon Shield Avatar Selected", Toast.LENGTH_SHORT).show()
        }

        // Gallery Picker
        binding.btnPickGallery.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }
    }

    private fun updateAvatarDisplay() {
        val customUri = prefManager.userAvatarUri
        if (customUri != null) {
            try {
                binding.ivCurrentAvatar.setImageURI(Uri.parse(customUri))
                return
            } catch (e: Exception) {
                // fallback
            }
        }

        when (prefManager.userAvatarType) {
            "TIGER" -> binding.ivCurrentAvatar.setImageResource(R.drawable.avatar_default_tiger)
            "ROBOT" -> binding.ivCurrentAvatar.setImageResource(R.drawable.avatar_default_robot)
            "CROWN" -> binding.ivCurrentAvatar.setImageResource(R.drawable.avatar_default_crown)
            "SHIELD" -> binding.ivCurrentAvatar.setImageResource(R.drawable.avatar_default_shield)
            else -> binding.ivCurrentAvatar.setImageResource(R.drawable.avatar_default_robot)
        }
    }

    private fun setupActions() {
        binding.btnCloseProfile.setOnClickListener {
            dismiss()
        }

        // Change Password Dialog
        binding.btnChangePassword.setOnClickListener {
            showChangePasswordDialog()
        }

        // Contact Support Dialog
        binding.btnContactSupport.setOnClickListener {
            showContactSupportDialog()
        }

        // Send Feedback Dialog
        binding.btnFeedback.setOnClickListener {
            showFeedbackDialog()
        }

        // Logout Button
        binding.btnLogout.setOnClickListener {
            showVipLogoutDialog()
        }
    }

    private fun showChangePasswordDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_vip_change_password, null)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val etCurrentPass = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etCurrentPassword)
        val etNewPass = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etNewPassword)
        val etConfirmNewPass = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etConfirmNewPassword)
        val btnCancel = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCancelChangePass)
        val btnSubmit = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSubmitChangePass)

        Tilt3DHelper.attach3DPressEffect(btnCancel)
        Tilt3DHelper.attach3DPressEffect(btnSubmit)

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnSubmit.setOnClickListener {
            val currentPass = etCurrentPass.text?.toString()?.trim() ?: ""
            val newPass = etNewPass.text?.toString()?.trim() ?: ""
            val confirm = etConfirmNewPass.text?.toString()?.trim() ?: ""

            if (currentPass.isEmpty()) {
                etCurrentPass.error = "Enter current password"
                return@setOnClickListener
            }
            if (newPass.length < 6) {
                etNewPass.error = "Minimum 6 characters required"
                return@setOnClickListener
            }
            if (newPass != confirm) {
                etConfirmNewPass.error = "Passwords do not match"
                return@setOnClickListener
            }

            prefManager.savedPassword = newPass
            dialog.dismiss()
            (activity as? GameWebActivity)?.showVipNotification("Password updated successfully!", R.drawable.icon_settings_3d)
                ?: Toast.makeText(requireContext(), "Password updated successfully!", Toast.LENGTH_SHORT).show()
        }

        dialog.show()
    }

    private fun showContactSupportDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_vip_contact_support, null)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val btnClose = dialogView.findViewById<android.widget.ImageButton>(R.id.btnCloseSupportDialog)
        val btnTelegram = dialogView.findViewById<View>(R.id.btnSupportTelegram)
        val btnWhatsApp = dialogView.findViewById<View>(R.id.btnSupportWhatsApp)
        val btnEmail = dialogView.findViewById<View>(R.id.btnSupportEmail)

        Tilt3DHelper.attach3DPressEffect(btnTelegram)
        Tilt3DHelper.attach3DPressEffect(btnWhatsApp)
        Tilt3DHelper.attach3DPressEffect(btnEmail)

        btnClose.setOnClickListener { dialog.dismiss() }

        btnTelegram.setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/TeamPanther_VIP"))
                startActivity(intent)
            } catch (e: Exception) {
                (activity as? GameWebActivity)?.showVipNotification("Telegram app not found", R.drawable.icon_radar_3d)
                    ?: Toast.makeText(requireContext(), "Telegram not installed", Toast.LENGTH_SHORT).show()
            }
        }

        btnWhatsApp.setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/919876543210"))
                startActivity(intent)
            } catch (e: Exception) {
                (activity as? GameWebActivity)?.showVipNotification("WhatsApp app not found", R.drawable.icon_radar_3d)
                    ?: Toast.makeText(requireContext(), "WhatsApp not installed", Toast.LENGTH_SHORT).show()
            }
        }

        btnEmail.setOnClickListener {
            val emailIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:support@teampanther.com"))
            startActivity(Intent.createChooser(emailIntent, "Send VIP Support Email"))
        }

        dialog.show()
    }

    private fun showFeedbackDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_vip_feedback, null)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val etMsg = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etFeedbackMessage)
        val btnCancel = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCancelFeedback)
        val btnSubmit = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSubmitFeedback)

        Tilt3DHelper.attach3DPressEffect(btnCancel)
        Tilt3DHelper.attach3DPressEffect(btnSubmit)

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnSubmit.setOnClickListener {
            val msg = etMsg.text?.toString()?.trim() ?: ""
            if (msg.isEmpty()) {
                etMsg.error = "Please write your feedback message"
                return@setOnClickListener
            }

            dialog.dismiss()
            (activity as? GameWebActivity)?.showVipNotification("Feedback sent to Admin!", R.drawable.icon_analytics_3d)
                ?: Toast.makeText(requireContext(), "Feedback submitted successfully!", Toast.LENGTH_SHORT).show()
        }

        dialog.show()
    }

    private fun showVipLogoutDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_vip_exit, null)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val btnStay = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnStay)
        val btnExit = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnExit)
        val ivExitIcon = dialogView.findViewById<android.widget.ImageView>(R.id.ivExitIcon)
        val tvExitTitle = dialogView.findViewById<android.widget.TextView>(R.id.tvExitTitle)
        val tvExitMessage = dialogView.findViewById<android.widget.TextView>(R.id.tvExitMessage)

        tvExitTitle?.text = "LOGOUT TeamPanther"
        tvExitMessage?.text = "Are you sure you want to log out of your VIP account session?"

        Tilt3DHelper.start3DFloating(ivExitIcon, floatDistanceDp = 4f, durationMs = 2000)
        Tilt3DHelper.attach3DPressEffect(btnStay)
        Tilt3DHelper.attach3DPressEffect(btnExit)

        btnExit.text = "LOGOUT"

        btnStay.setOnClickListener {
            dialog.dismiss()
        }

        btnExit.setOnClickListener {
            dialog.dismiss()
            prefManager.clearSession()
            val intent = Intent(requireContext(), AuthActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            dismiss()
        }

        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "UserProfileBottomSheet"

        fun newInstance(): UserProfileBottomSheet {
            return UserProfileBottomSheet()
        }
    }
}


