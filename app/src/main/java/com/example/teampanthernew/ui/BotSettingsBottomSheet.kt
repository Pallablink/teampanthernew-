package com.example.teampanthernew.ui

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.teampanthernew.R
import com.example.teampanthernew.databinding.DialogVipBotSettingsBinding
import com.example.teampanthernew.utils.Tilt3DHelper
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import java.util.Locale

class BotSettingsBottomSheet : BottomSheetDialogFragment() {

    private var _binding: DialogVipBotSettingsBinding? = null
    private val binding get() = _binding!!

    private var selectedStrategy: String = "AI Neural Predictor"
    private var is2x: Boolean = true

    var onSaveListener: ((strategy: String, targetProfit: Double, firstBet: Double, maxLevels: Int, is2x: Boolean) -> Unit)? = null
    var onOpenCalculatorClicked: (() -> Unit)? = null
    var onOpenProfileClicked: (() -> Unit)? = null

    override fun getTheme(): Int = R.style.BottomSheetDialogTheme

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogVipBotSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        selectedStrategy = arguments?.getString(ARG_STRATEGY, "AI Neural Predictor") ?: "AI Neural Predictor"
        val currentTarget = arguments?.getDouble(ARG_TARGET_PROFIT, 500.0) ?: 500.0
        val currentFirstBet = arguments?.getDouble(ARG_FIRST_BET, 1.0) ?: 1.0
        val currentMaxLevels = arguments?.getInt(ARG_MAX_LEVELS, 10) ?: 10
        is2x = arguments?.getBoolean(ARG_IS_2X, true) ?: true

        // Inputs
        binding.etSettingsTargetProfit.setText(String.format(Locale.US, "%.2f", currentTarget))
        binding.etSettingsFirstBet.setText(String.format(Locale.US, "%.2f", currentFirstBet))
        binding.etSettingsMaxLevels.setText(currentMaxLevels.toString())

        updateStrategyUi()
        updateMultiplierUi()
        setupListeners()
    }

    private fun updateStrategyUi() {
        val isNeural = selectedStrategy.contains("neural", ignoreCase = true)
        val isTeamPantherVip = selectedStrategy.equals("TEAM PANTHER VIP", ignoreCase = true)
        val isTeamPantherVip2 = selectedStrategy.equals("TEAM PANTHER VIP 2", ignoreCase = true)
        val isTrendStreak = selectedStrategy.equals("Trend Streak Strategy", ignoreCase = true) || selectedStrategy.contains("trend streak", ignoreCase = true)
        val isDragon = selectedStrategy.contains("dragon", ignoreCase = true) && !isTrendStreak
        val isMaster = selectedStrategy.contains("master", ignoreCase = true) || selectedStrategy.contains("alternation", ignoreCase = true)
        val isUniversal = selectedStrategy.contains("universal", ignoreCase = true) || selectedStrategy.contains("digital", ignoreCase = true)

        binding.cardAiNeuralPredictor.setBackgroundResource(if (isNeural) R.drawable.bg_strategy_card_active else R.drawable.bg_strategy_card_inactive)
        binding.tvBadgeAiNeural.visibility = if (isNeural) View.VISIBLE else View.GONE

        binding.cardTeamPantherVip.setBackgroundResource(if (isTeamPantherVip) R.drawable.bg_strategy_card_active else R.drawable.bg_strategy_card_inactive)
        binding.tvBadgeTeamPantherVip.visibility = if (isTeamPantherVip) View.VISIBLE else View.GONE

        binding.cardTeamPantherVip2.setBackgroundResource(if (isTeamPantherVip2) R.drawable.bg_strategy_card_active else R.drawable.bg_strategy_card_inactive)
        binding.tvBadgeTeamPantherVip2.visibility = if (isTeamPantherVip2) View.VISIBLE else View.GONE

        binding.cardTrendStreak.setBackgroundResource(if (isTrendStreak) R.drawable.bg_strategy_card_active else R.drawable.bg_strategy_card_inactive)
        binding.tvBadgeTrendStreak.visibility = if (isTrendStreak) View.VISIBLE else View.GONE

        binding.cardDragonStrategy.setBackgroundResource(if (isDragon) R.drawable.bg_strategy_card_active else R.drawable.bg_strategy_card_inactive)
        binding.tvBadgeDragon.visibility = if (isDragon) View.VISIBLE else View.GONE

        binding.cardMasterBot.setBackgroundResource(if (isMaster) R.drawable.bg_strategy_card_active else R.drawable.bg_strategy_card_inactive)
        binding.tvBadgeMaster.visibility = if (isMaster) View.VISIBLE else View.GONE

        binding.cardUniversalBot.setBackgroundResource(if (isUniversal) R.drawable.bg_strategy_card_active else R.drawable.bg_strategy_card_inactive)
        binding.tvBadgeUniversal.visibility = if (isUniversal) View.VISIBLE else View.GONE
    }

    private fun setupListeners() {
        // Strategy Selectors
        binding.cardAiNeuralPredictor.setOnClickListener {
            selectedStrategy = "AI Neural Predictor"
            updateStrategyUi()
        }
        binding.cardTeamPantherVip.setOnClickListener {
            selectedStrategy = "TEAM PANTHER VIP"
            updateStrategyUi()
        }
        binding.cardTeamPantherVip2.setOnClickListener {
            selectedStrategy = "TEAM PANTHER VIP 2"
            updateStrategyUi()
        }
        binding.cardTrendStreak.setOnClickListener {
            selectedStrategy = "Trend Streak Strategy"
            updateStrategyUi()
        }
        binding.cardDragonStrategy.setOnClickListener {
            selectedStrategy = "Dragon Strategy"
            updateStrategyUi()
        }
        binding.cardMasterBot.setOnClickListener {
            selectedStrategy = "Master Bot"
            updateStrategyUi()
        }
        binding.cardUniversalBot.setOnClickListener {
            selectedStrategy = "Universal Bot"
            updateStrategyUi()
        }

        // Multipliers
        binding.btnSettings2x.setOnClickListener {
            is2x = true
            updateMultiplierUi()
        }
        binding.btnSettings3x.setOnClickListener {
            is2x = false
            updateMultiplierUi()
        }

        // Target Presets
        binding.chipTarget200.setOnClickListener { binding.etSettingsTargetProfit.setText("200.00") }
        binding.chipTarget500.setOnClickListener { binding.etSettingsTargetProfit.setText("500.00") }
        binding.chipTarget1k.setOnClickListener { binding.etSettingsTargetProfit.setText("1000.00") }
        binding.chipTarget2k.setOnClickListener { binding.etSettingsTargetProfit.setText("2000.00") }
        binding.chipTarget5k.setOnClickListener { binding.etSettingsTargetProfit.setText("5000.00") }

        // Bet Presets
        binding.chipBet1.setOnClickListener { binding.etSettingsFirstBet.setText("1.00") }
        binding.chipBet2.setOnClickListener { binding.etSettingsFirstBet.setText("2.00") }
        binding.chipBet6.setOnClickListener { binding.etSettingsFirstBet.setText("6.00") }
        binding.chipBet10.setOnClickListener { binding.etSettingsFirstBet.setText("10.00") }
        binding.chipBet20.setOnClickListener { binding.etSettingsFirstBet.setText("20.00") }
        binding.chipBet50.setOnClickListener { binding.etSettingsFirstBet.setText("50.00") }

        // Level Presets
        binding.chipLvl5.setOnClickListener { binding.etSettingsMaxLevels.setText("5") }
        binding.chipLvl6.setOnClickListener { binding.etSettingsMaxLevels.setText("6") }
        binding.chipLvl7.setOnClickListener { binding.etSettingsMaxLevels.setText("7") }
        binding.chipLvl8.setOnClickListener { binding.etSettingsMaxLevels.setText("8") }
        binding.chipLvl10.setOnClickListener { binding.etSettingsMaxLevels.setText("10") }

        // 3D Press Effects
        Tilt3DHelper.attach3DPressEffect(binding.btnSettingsSave)
        Tilt3DHelper.attach3DPressEffect(binding.btnSettingsCancel)
        Tilt3DHelper.attach3DPressEffect(binding.btnSettingsOpenCalculator)
        Tilt3DHelper.attach3DPressEffect(binding.btnSettingsOpenProfile)
        Tilt3DHelper.attach3DPressEffect(binding.cardAiNeuralPredictor)
        Tilt3DHelper.attach3DPressEffect(binding.cardTeamPantherVip)
        Tilt3DHelper.attach3DPressEffect(binding.cardTeamPantherVip2)
        Tilt3DHelper.attach3DPressEffect(binding.cardTrendStreak)
        Tilt3DHelper.attach3DPressEffect(binding.cardDragonStrategy)
        Tilt3DHelper.attach3DPressEffect(binding.cardMasterBot)
        Tilt3DHelper.attach3DPressEffect(binding.cardUniversalBot)

        // Cancel
        binding.btnSettingsCancel.setOnClickListener {
            dismiss()
        }

        // Open Calculator Shortcut
        binding.btnSettingsOpenCalculator.setOnClickListener {
            dismissAllowingStateLoss()
            onOpenCalculatorClicked?.invoke()
        }

        // Open User Profile Shortcut
        binding.btnSettingsOpenProfile.setOnClickListener {
            dismissAllowingStateLoss()
            onOpenProfileClicked?.invoke()
        }

        // Save & Apply
        binding.btnSettingsSave.setOnClickListener {
            val target = binding.etSettingsTargetProfit.text.toString().toDoubleOrNull() ?: 500.0
            val firstBet = binding.etSettingsFirstBet.text.toString().toDoubleOrNull() ?: 1.0
            val maxLevels = binding.etSettingsMaxLevels.text.toString().toIntOrNull()?.coerceIn(1, 15) ?: 10

            onSaveListener?.invoke(selectedStrategy, target, firstBet, maxLevels, is2x)
            dismiss()
        }
    }

    private fun updateMultiplierUi() {
        if (is2x) {
            binding.btnSettings2x.setBackgroundResource(R.drawable.bg_vip_toggle_active_2x)
            binding.btnSettings2x.setTextColor(Color.WHITE)
            binding.btnSettings3x.setBackgroundResource(R.drawable.bg_vip_toggle_inactive)
            binding.btnSettings3x.setTextColor(Color.parseColor("#94A3B8"))
        } else {
            binding.btnSettings3x.setBackgroundResource(R.drawable.bg_vip_toggle_active_3x)
            binding.btnSettings3x.setTextColor(Color.WHITE)
            binding.btnSettings2x.setBackgroundResource(R.drawable.bg_vip_toggle_inactive)
            binding.btnSettings2x.setTextColor(Color.parseColor("#94A3B8"))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "BotSettingsBottomSheet"
        private const val ARG_STRATEGY = "arg_strategy"
        private const val ARG_TARGET_PROFIT = "arg_target_profit"
        private const val ARG_FIRST_BET = "arg_first_bet"
        private const val ARG_MAX_LEVELS = "arg_max_levels"
        private const val ARG_IS_2X = "arg_is_2x"

        fun newInstance(
            strategy: String = "AI Neural Predictor",
            targetProfit: Double,
            firstBet: Double,
            maxLevels: Int,
            is2x: Boolean
        ): BotSettingsBottomSheet {
            val sheet = BotSettingsBottomSheet()
            val args = Bundle().apply {
                putString(ARG_STRATEGY, strategy)
                putDouble(ARG_TARGET_PROFIT, targetProfit)
                putDouble(ARG_FIRST_BET, firstBet)
                putInt(ARG_MAX_LEVELS, maxLevels)
                putBoolean(ARG_IS_2X, is2x)
            }
            sheet.arguments = args
            return sheet
        }
    }
}


