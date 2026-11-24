package com.example.teampanthernew.ui

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.example.teampanthernew.R
import com.example.teampanthernew.data.AutoBetRecord
import com.example.teampanthernew.databinding.BottomSheetBetHistoryBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import java.util.Locale

class BetHistoryBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetBetHistoryBinding? = null
    private val binding get() = _binding!!

    override fun getTheme(): Int = R.style.BottomSheetDialogTheme

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetBetHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val totalBets = arguments?.getInt(ARG_TOTAL_BETS, 0) ?: 0
        val wins = arguments?.getInt(ARG_WINS, 0) ?: 0
        val losses = arguments?.getInt(ARG_LOSSES, 0) ?: 0
        val profit = arguments?.getDouble(ARG_PROFIT, 0.0) ?: 0.0

        @Suppress("DEPRECATION", "UNCHECKED_CAST")
        val records = arguments?.getSerializable(ARG_RECORDS) as? ArrayList<AutoBetRecord> ?: arrayListOf()

        binding.tvHistoryTotalBets.text = "$totalBets"
        binding.tvHistoryWinLoss.text = "$wins / $losses"
        binding.tvHistoryNetProfit.text = if (profit >= 0) "+₹${String.format(Locale.US, "%.2f", profit)}" else "-₹${String.format(Locale.US, "%.2f", Math.abs(profit))}"
        binding.tvHistoryNetProfit.setTextColor(if (profit >= 0) Color.parseColor("#10B981") else Color.parseColor("#EF4444"))

        binding.btnCloseHistory.setOnClickListener {
            dismiss()
        }

        renderRecords(records)
    }

    private fun renderRecords(records: List<AutoBetRecord>) {
        if (records.isEmpty()) {
            binding.layoutEmptyHistory.visibility = View.VISIBLE
            binding.scrollHistoryRecords.visibility = View.GONE
            return
        }

        binding.layoutEmptyHistory.visibility = View.GONE
        binding.scrollHistoryRecords.visibility = View.VISIBLE
        binding.llHistoryRecordsList.removeAllViews()

        for (rec in records.reversed()) { // Show latest bet first
            val rowView = layoutInflater.inflate(R.layout.item_bet_history_row, binding.llHistoryRecordsList, false)
            val tvPeriod = rowView.findViewById<TextView>(R.id.tvRowPeriod)
            val tvChoice = rowView.findViewById<TextView>(R.id.tvRowChoice)
            val tvLevel = rowView.findViewById<TextView>(R.id.tvRowLevel)
            val tvStatusBadge = rowView.findViewById<TextView>(R.id.tvRowStatusBadge)
            val tvStake = rowView.findViewById<TextView>(R.id.tvRowStake)
            val tvProfit = rowView.findViewById<TextView>(R.id.tvRowProfit)
            val tvTime = rowView.findViewById<TextView>(R.id.tvRowTime)

            val pShort = if (rec.periodId.length >= 4) rec.periodId.takeLast(4) else rec.periodId
            tvPeriod.text = "#$pShort"
            tvChoice.text = rec.choice
            tvChoice.setTextColor(if (rec.choice.equals("BIG", true)) Color.parseColor("#F59E0B") else Color.parseColor("#00E5FF"))
            tvLevel.text = "LVL ${rec.level}"
            tvStake.text = "Stake: ₹${String.format(Locale.US, "%.2f", rec.stake)}"
            tvTime.text = rec.timestamp

            when (rec.status.uppercase()) {
                "WIN" -> {
                    tvStatusBadge.text = "WIN"
                    tvStatusBadge.setBackgroundResource(R.drawable.bg_history_w)
                    tvProfit.text = "+₹${String.format(Locale.US, "%.2f", rec.profit)}"
                    tvProfit.setTextColor(Color.parseColor("#10B981"))
                }
                "LOSS" -> {
                    tvStatusBadge.text = "LOSS"
                    tvStatusBadge.setBackgroundResource(R.drawable.bg_history_l)
                    tvProfit.text = "-₹${String.format(Locale.US, "%.2f", rec.stake)}"
                    tvProfit.setTextColor(Color.parseColor("#EF4444"))
                }
                else -> {
                    tvStatusBadge.text = "PENDING"
                    tvStatusBadge.setBackgroundResource(R.drawable.bg_history_b)
                    tvProfit.text = "Running..."
                    tvProfit.setTextColor(Color.parseColor("#F59E0B"))
                }
            }

            binding.llHistoryRecordsList.addView(rowView)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "BetHistoryBottomSheet"
        private const val ARG_TOTAL_BETS = "arg_total_bets"
        private const val ARG_WINS = "arg_wins"
        private const val ARG_LOSSES = "arg_losses"
        private const val ARG_PROFIT = "arg_profit"
        private const val ARG_RECORDS = "arg_records"

        fun newInstance(
            totalBets: Int,
            wins: Int,
            losses: Int,
            profit: Double,
            records: ArrayList<AutoBetRecord> = arrayListOf()
        ): BetHistoryBottomSheet {
            val sheet = BetHistoryBottomSheet()
            val args = Bundle().apply {
                putInt(ARG_TOTAL_BETS, totalBets)
                putInt(ARG_WINS, wins)
                putInt(ARG_LOSSES, losses)
                putDouble(ARG_PROFIT, profit)
                putSerializable(ARG_RECORDS, records)
            }
            sheet.arguments = args
            return sheet
        }
    }
}


