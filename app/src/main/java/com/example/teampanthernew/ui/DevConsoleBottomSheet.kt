package com.example.teampanthernew.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.example.teampanthernew.R
import com.example.teampanthernew.databinding.BottomSheetDevConsoleBinding
import com.example.teampanthernew.utils.DevConsoleEntry
import com.example.teampanthernew.utils.DevInspectorManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class DevConsoleBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetDevConsoleBinding? = null
    private val binding get() = _binding!!

    private var currentFilter = FILTER_ALL

    companion object {
        const val TAG = "DevConsoleBottomSheet"
        private const val FILTER_ALL = 0
        private const val FILTER_ERRORS = 1
        private const val FILTER_WARNINGS = 2

        fun newInstance(): DevConsoleBottomSheet = DevConsoleBottomSheet()
    }

    override fun getTheme(): Int = R.style.BottomSheetDialogTheme

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetDevConsoleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupListeners()
        updateFilterButtonsUI()
        populateConsoleList()
    }

    private fun setupListeners() {
        binding.btnConsoleFilterAll.setOnClickListener {
            currentFilter = FILTER_ALL
            updateFilterButtonsUI()
            populateConsoleList()
        }

        binding.btnConsoleFilterErrors.setOnClickListener {
            currentFilter = FILTER_ERRORS
            updateFilterButtonsUI()
            populateConsoleList()
        }

        binding.btnConsoleFilterWarnings.setOnClickListener {
            currentFilter = FILTER_WARNINGS
            updateFilterButtonsUI()
            populateConsoleList()
        }

        binding.btnClearConsole.setOnClickListener {
            DevInspectorManager.clearConsole()
            populateConsoleList()
            Toast.makeText(requireContext(), "Console cleared", Toast.LENGTH_SHORT).show()
        }

        binding.btnCloseConsole.setOnClickListener {
            dismiss()
        }

        binding.btnCopyConsole.setOnClickListener {
            val text = DevInspectorManager.getAllConsoleAsText()
            if (text.isNotBlank()) {
                val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("TeamPanther_ConsoleLogs", text)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(requireContext(), "Console logs copied to clipboard!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "No console logs to copy", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateFilterButtonsUI() {
        binding.btnConsoleFilterAll.backgroundTintList = ContextCompat.getColorStateList(
            requireContext(),
            if (currentFilter == FILTER_ALL) R.color.vip_gold else android.R.color.transparent
        )
        binding.btnConsoleFilterAll.setTextColor(if (currentFilter == FILTER_ALL) Color.BLACK else Color.parseColor("#9CA3AF"))

        binding.btnConsoleFilterErrors.backgroundTintList = ContextCompat.getColorStateList(
            requireContext(),
            if (currentFilter == FILTER_ERRORS) R.color.vip_gold else android.R.color.transparent
        )
        binding.btnConsoleFilterErrors.setTextColor(if (currentFilter == FILTER_ERRORS) Color.BLACK else Color.parseColor("#EF4444"))

        binding.btnConsoleFilterWarnings.backgroundTintList = ContextCompat.getColorStateList(
            requireContext(),
            if (currentFilter == FILTER_WARNINGS) R.color.vip_gold else android.R.color.transparent
        )
        binding.btnConsoleFilterWarnings.setTextColor(if (currentFilter == FILTER_WARNINGS) Color.BLACK else Color.parseColor("#F59E0B"))
    }

    private fun populateConsoleList() {
        val container = binding.layoutConsoleList
        container.removeAllViews()

        val allEntries = DevInspectorManager.consoleEntries.toList()
        val total = allEntries.size
        val errors = DevInspectorManager.getConsoleErrorCount()

        binding.tvConsoleSummary.text = "Total Logs: $total | Errors: $errors"

        val filtered = when (currentFilter) {
            FILTER_ERRORS -> allEntries.filter { it.level == "ERROR" }
            FILTER_WARNINGS -> allEntries.filter { it.level == "WARNING" || it.level == "WARN" }
            else -> allEntries
        }

        if (filtered.isEmpty()) {
            val emptyTv = TextView(requireContext()).apply {
                text = if (total == 0) "No console output logged yet." else "No logs match the selected filter."
                textSize = 13f
                setTextColor(Color.parseColor("#6B7280"))
                gravity = Gravity.CENTER
                setPadding(0, 48, 0, 48)
            }
            container.addView(emptyTv)
            return
        }

        val density = resources.displayMetrics.density

        filtered.forEach { entry ->
            val cardView = createConsoleEntryView(entry, density)
            container.addView(cardView)
        }
    }

    private fun createConsoleEntryView(entry: DevConsoleEntry, density: Float): View {
        val ctx = requireContext()
        val rowLayout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (10 * density).toInt()
            setPadding(pad, pad, pad, pad)
            val marginBot = (8 * density).toInt()
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, marginBot)
            }
            layoutParams = lp
            setBackgroundColor(Color.parseColor("#181B24"))
        }

        // Top Row: Level badge, Source, Time
        val topRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val (badgeBgColor, badgeTextColor) = when (entry.level) {
            "ERROR" -> Pair(Color.parseColor("#DC2626"), Color.WHITE)
            "WARNING", "WARN" -> Pair(Color.parseColor("#D97706"), Color.WHITE)
            "DEBUG" -> Pair(Color.parseColor("#7C3AED"), Color.WHITE)
            else -> Pair(Color.parseColor("#374151"), Color.parseColor("#D1D5DB"))
        }

        val tvLevel = TextView(ctx).apply {
            text = entry.level
            textSize = 10f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(badgeTextColor)
            setBackgroundColor(badgeBgColor)
            val hPad = (6 * density).toInt()
            val vPad = (2 * density).toInt()
            setPadding(hPad, vPad, hPad, vPad)
        }
        topRow.addView(tvLevel)

        // Source filename / line
        if (entry.source.isNotEmpty()) {
            val tvSource = TextView(ctx).apply {
                text = entry.source
                textSize = 10f
                setTextColor(Color.parseColor("#9CA3AF"))
                val sPad = (8 * density).toInt()
                setPadding(sPad, 0, 0, 0)
            }
            topRow.addView(tvSource)
        }

        // Spacer
        val spacer = View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
        }
        topRow.addView(spacer)

        // Time
        val tvTime = TextView(ctx).apply {
            text = entry.time
            textSize = 10f
            setTextColor(Color.parseColor("#6B7280"))
        }
        topRow.addView(tvTime)

        rowLayout.addView(topRow)

        // Message
        val tvMessage = TextView(ctx).apply {
            text = entry.message
            textSize = 12f
            val msgColor = when (entry.level) {
                "ERROR" -> Color.parseColor("#FCA5A5")
                "WARNING", "WARN" -> Color.parseColor("#FDE68A")
                else -> Color.parseColor("#E5E7EB")
            }
            setTextColor(msgColor)
            setTextIsSelectable(true)
            val vPad = (6 * density).toInt()
            setPadding(0, vPad, 0, 0)
        }
        rowLayout.addView(tvMessage)

        // Click to copy log
        rowLayout.setOnClickListener {
            val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val textToCopy = "[${entry.level}] ${entry.message} (${entry.source})"
            val clip = ClipData.newPlainText("ConsoleLog", textToCopy)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(ctx, "Log copied to clipboard!", Toast.LENGTH_SHORT).show()
        }

        return rowLayout
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
