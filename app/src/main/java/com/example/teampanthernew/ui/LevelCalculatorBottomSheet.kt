package com.example.teampanthernew.ui

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.example.teampanthernew.R
import com.example.teampanthernew.databinding.DialogVipLevelCalculatorBinding
import com.example.teampanthernew.utils.Tilt3DHelper
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import java.util.Locale

class LevelCalculatorBottomSheet : BottomSheetDialogFragment() {

    private var _binding: DialogVipLevelCalculatorBinding? = null
    private val binding get() = _binding!!

    private var walletBalance: Double = 0.0
    private var is2x: Boolean = true
    private var calculatedBaseBet: Int = 1
    private var targetLevels: Int = 10

    var onApplyListener: ((baseBet: Double, levels: Int, stopLoss: Double, is2x: Boolean) -> Unit)? = null

    override fun getTheme(): Int = R.style.BottomSheetDialogTheme

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogVipLevelCalculatorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        walletBalance = arguments?.getDouble(ARG_WALLET_BALANCE, 0.92) ?: 0.92
        val initialBudget = arguments?.getDouble(ARG_INITIAL_BUDGET, 2000.0) ?: 2000.0
        val initialLevels = arguments?.getInt(ARG_INITIAL_LEVELS, 10) ?: 10
        is2x = arguments?.getBoolean(ARG_IS_2X, true) ?: true

        binding.tvCalcWalletRef.text = "Wallet: ₹${String.format(Locale.US, "%.2f", walletBalance)}"
        binding.etCalcAffordableLoss.setText(String.format(Locale.US, "%.0f", initialBudget))
        binding.etCalcLevels.setText(initialLevels.toString())

        setupInteractions()
        updateMultiplierUi()
        calculateAndRender()
    }

    private fun setupInteractions() {
        // Multiplier Buttons
        binding.btnCalc2x.setOnClickListener {
            is2x = true
            updateMultiplierUi()
            calculateAndRender()
        }
        binding.btnCalc3x.setOnClickListener {
            is2x = false
            updateMultiplierUi()
            calculateAndRender()
        }

        // Budget Presets
        binding.chipBudget100.setOnClickListener {
            binding.etCalcAffordableLoss.setText("100")
            calculateAndRender()
        }
        binding.chipBudget500.setOnClickListener {
            binding.etCalcAffordableLoss.setText("500")
            calculateAndRender()
        }
        binding.chipBudget1k.setOnClickListener {
            binding.etCalcAffordableLoss.setText("1000")
            calculateAndRender()
        }
        binding.chipBudget2k.setOnClickListener {
            binding.etCalcAffordableLoss.setText("2000")
            calculateAndRender()
        }
        binding.chipBudget5k.setOnClickListener {
            binding.etCalcAffordableLoss.setText("5000")
            calculateAndRender()
        }
        binding.chipBudget10k.setOnClickListener {
            binding.etCalcAffordableLoss.setText("10000")
            calculateAndRender()
        }

        // Level Presets
        binding.chipLvl5.setOnClickListener {
            binding.etCalcLevels.setText("5")
            calculateAndRender()
        }
        binding.chipLvl6.setOnClickListener {
            binding.etCalcLevels.setText("6")
            calculateAndRender()
        }
        binding.chipLvl7.setOnClickListener {
            binding.etCalcLevels.setText("7")
            calculateAndRender()
        }
        binding.chipLvl8.setOnClickListener {
            binding.etCalcLevels.setText("8")
            calculateAndRender()
        }
        binding.chipLvl10.setOnClickListener {
            binding.etCalcLevels.setText("10")
            calculateAndRender()
        }

        // Live text watchers
        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                calculateAndRender()
            }
            override fun afterTextChanged(s: Editable?) {}
        }
        binding.etCalcAffordableLoss.addTextChangedListener(textWatcher)
        binding.etCalcLevels.addTextChangedListener(textWatcher)

        // 3D Press Effects
        Tilt3DHelper.attach3DPressEffect(binding.btnCalcApplyToBot)
        Tilt3DHelper.attach3DPressEffect(binding.btnCalcClose)
        Tilt3DHelper.attach3DPressEffect(binding.btnCalc2x)
        Tilt3DHelper.attach3DPressEffect(binding.btnCalc3x)

        // Close
        binding.btnCalcClose.setOnClickListener {
            dismiss()
        }

        // Apply to Bot
        binding.btnCalcApplyToBot.setOnClickListener {
            val budget = binding.etCalcAffordableLoss.text.toString().toDoubleOrNull() ?: 2000.0
            onApplyListener?.invoke(calculatedBaseBet.toDouble(), targetLevels, budget, is2x)
            dismiss()
        }
    }

    private fun updateMultiplierUi() {
        if (is2x) {
            binding.btnCalc2x.setBackgroundResource(R.drawable.bg_vip_toggle_active_2x)
            binding.btnCalc2x.setTextColor(Color.WHITE)
            binding.btnCalc3x.setBackgroundResource(R.drawable.bg_vip_toggle_inactive)
            binding.btnCalc3x.setTextColor(Color.parseColor("#94A3B8"))
            binding.tvCalcMultiplierTag.text = "2x MARTINGALE"
            binding.tvCalcMultiplierTag.setBackgroundResource(R.drawable.bg_history_b)
        } else {
            binding.btnCalc3x.setBackgroundResource(R.drawable.bg_vip_toggle_active_3x)
            binding.btnCalc3x.setTextColor(Color.WHITE)
            binding.btnCalc2x.setBackgroundResource(R.drawable.bg_vip_toggle_inactive)
            binding.btnCalc2x.setTextColor(Color.parseColor("#94A3B8"))
            binding.tvCalcMultiplierTag.text = "3x LEVEL TRICK"
            binding.tvCalcMultiplierTag.setBackgroundResource(R.drawable.bg_history_s)
        }
    }

    private fun calculateAndRender() {
        val budget = binding.etCalcAffordableLoss.text.toString().toDoubleOrNull() ?: 0.0
        val levels = binding.etCalcLevels.text.toString().toIntOrNull()?.coerceIn(1, 15) ?: 0

        if (budget <= 0 || levels <= 0) {
            binding.tvCalcRecommendedBet.text = "₹1.00"
            binding.tvCalcTotalRisk.text = "₹0.00"
            binding.tvCalcBuffer.text = "₹0.00"
            binding.tvCalcProtection.text = "0 Losses"
            binding.tvCalcStatusNote.text = "Please enter a valid loss budget and level count."
            binding.tvCalcStatusNote.setTextColor(Color.parseColor("#EF4444"))
            binding.btnCalcApplyToBot.isEnabled = false
            binding.llCalcBreakdownList.removeAllViews()
            return
        }

        binding.btnCalcApplyToBot.isEnabled = true
        targetLevels = levels
        val multRatio = if (is2x) 2.0 else 3.0

        // Multiplier Sum: Sum_{k=0..levels-1} multRatio^k
        var sumMult = 0.0
        var currentMult = 1.0
        val levelMultipliers = mutableListOf<Double>()
        for (i in 0 until levels) {
            sumMult += currentMult
            levelMultipliers.add(currentMult)
            currentMult *= multRatio
        }

        // Raw base bet floored to integer (minimum ₹1)
        val exactBase = budget / sumMult
        calculatedBaseBet = Math.floor(exactBase).toInt().coerceAtLeast(1)

        val totalRisk = calculatedBaseBet * sumMult
        val buffer = budget - totalRisk

        binding.tvCalcRecommendedBet.text = "₹" + String.format(Locale.US, "%,.2f", calculatedBaseBet.toDouble())
        binding.tvCalcTotalRisk.text = "₹" + String.format(Locale.US, "%,.2f", totalRisk)
        binding.tvCalcBuffer.text = (if (buffer >= 0) "+₹" else "-₹") + String.format(Locale.US, "%,.2f", Math.abs(buffer))
        binding.tvCalcProtection.text = "$levels Losses"

        if (buffer >= 0) {
            binding.tvCalcStatusNote.text = "Safe plan: Covers all $levels levels within your ₹${String.format(Locale.US, "%,.0f", budget)} budget with ₹${String.format(Locale.US, "%,.0f", buffer)} buffer remaining."
            binding.tvCalcStatusNote.setTextColor(Color.parseColor("#10B981"))
        } else {
            binding.tvCalcStatusNote.text = "Minimum bet ₹$calculatedBaseBet requires ₹${String.format(Locale.US, "%,.0f", totalRisk)} for $levels levels (needs ₹${String.format(Locale.US, "%,.0f", -buffer)} more capital)."
            binding.tvCalcStatusNote.setTextColor(Color.parseColor("#F59E0B"))
        }

        binding.btnCalcApplyToBot.text = "APPLY TO ROBOT (SET ₹$calculatedBaseBet, $levels LVL & SL ₹${String.format(Locale.US, "%,.0f", budget)})"

        // Build dynamic level rows in llCalcBreakdownList
        binding.llCalcBreakdownList.removeAllViews()
        var runningLoss = 0.0

        // Header Row
        val headerRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(12, 6, 12, 6)
            weightSum = 10f
        }
        val th1 = TextView(requireContext()).apply {
            text = "LEVEL"
            textSize = 9.5f
            setTextColor(Color.parseColor("#64748B"))
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 2.5f)
        }
        val th2 = TextView(requireContext()).apply {
            text = "BET STAKE"
            textSize = 9.5f
            setTextColor(Color.parseColor("#64748B"))
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 3.5f)
        }
        val th3 = TextView(requireContext()).apply {
            text = "TOTAL RISK"
            textSize = 9.5f
            setTextColor(Color.parseColor("#64748B"))
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 4f)
        }
        headerRow.addView(th1)
        headerRow.addView(th2)
        headerRow.addView(th3)
        binding.llCalcBreakdownList.addView(headerRow)

        // Divider
        val divider = View(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1).apply {
                topMargin = 4
                bottomMargin = 6
            }
            setBackgroundColor(Color.parseColor("#1E293B"))
        }
        binding.llCalcBreakdownList.addView(divider)

        // Rows
        for (i in 1..levels) {
            val m = levelMultipliers[i - 1]
            val lvlStake = Math.round(calculatedBaseBet * m).toInt()
            runningLoss += lvlStake

            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(12, 8, 12, 8)
                weightSum = 10f
            }

            val tvLvl = TextView(requireContext()).apply {
                text = "Lav $i"
                textSize = 10.5f
                setTextColor(if (i == 1) Color.parseColor("#00E5FF") else Color.parseColor("#E2E8F0"))
                setTypeface(null, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 2.5f)
            }

            val tvStake = TextView(requireContext()).apply {
                text = "₹" + String.format(Locale.US, "%,d", lvlStake)
                textSize = 10.5f
                setTextColor(Color.parseColor("#FFB703"))
                setTypeface(null, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 3.5f)
            }

            val tvCumulative = TextView(requireContext()).apply {
                text = "₹" + String.format(Locale.US, "%,.0f", runningLoss)
                textSize = 10.5f
                setTextColor(if (runningLoss <= budget) Color.parseColor("#10B981") else Color.parseColor("#EF4444"))
                setTypeface(null, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 4f)
            }

            row.addView(tvLvl)
            row.addView(tvStake)
            row.addView(tvCumulative)
            binding.llCalcBreakdownList.addView(row)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "LevelCalculatorBottomSheet"
        private const val ARG_WALLET_BALANCE = "wallet_balance"
        private const val ARG_INITIAL_BUDGET = "initial_budget"
        private const val ARG_INITIAL_LEVELS = "initial_levels"
        private const val ARG_IS_2X = "is_2x"

        fun newInstance(
            walletBalance: Double,
            initialBudget: Double = 2000.0,
            initialLevels: Int = 10,
            is2x: Boolean = true
        ): LevelCalculatorBottomSheet {
            val sheet = LevelCalculatorBottomSheet()
            val args = Bundle().apply {
                putDouble(ARG_WALLET_BALANCE, walletBalance)
                putDouble(ARG_INITIAL_BUDGET, initialBudget)
                putInt(ARG_INITIAL_LEVELS, initialLevels)
                putBoolean(ARG_IS_2X, is2x)
            }
            sheet.arguments = args
            return sheet
        }
    }
}


