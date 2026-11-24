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
import com.example.teampanthernew.databinding.BottomSheetDevNetworkBinding
import com.example.teampanthernew.utils.DevInspectorManager
import com.example.teampanthernew.utils.DevNetworkEntry
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class DevNetworkBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetDevNetworkBinding? = null
    private val binding get() = _binding!!

    private var currentFilter = FILTER_ALL

    companion object {
        const val TAG = "DevNetworkBottomSheet"
        private const val FILTER_ALL = 0
        private const val FILTER_XHR = 1
        private const val FILTER_ERRORS = 2

        fun newInstance(): DevNetworkBottomSheet = DevNetworkBottomSheet()
    }

    override fun getTheme(): Int = R.style.BottomSheetDialogTheme

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetDevNetworkBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupListeners()
        updateFilterButtonsUI()
        populateNetworkList()
    }

    private fun setupListeners() {
        binding.btnFilterAll.setOnClickListener {
            currentFilter = FILTER_ALL
            updateFilterButtonsUI()
            populateNetworkList()
        }

        binding.btnFilterXhr.setOnClickListener {
            currentFilter = FILTER_XHR
            updateFilterButtonsUI()
            populateNetworkList()
        }

        binding.btnFilterErrors.setOnClickListener {
            currentFilter = FILTER_ERRORS
            updateFilterButtonsUI()
            populateNetworkList()
        }

        binding.btnClearNetwork.setOnClickListener {
            DevInspectorManager.clearNetwork()
            populateNetworkList()
            Toast.makeText(requireContext(), "Network log cleared", Toast.LENGTH_SHORT).show()
        }

        binding.btnCloseNetwork.setOnClickListener {
            dismiss()
        }

        binding.btnCopyNetwork.setOnClickListener {
            val text = DevInspectorManager.getAllNetworkAsText()
            if (text.isNotBlank()) {
                val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("TeamPanther_NetworkLogs", text)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(requireContext(), "Network logs copied to clipboard!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "No network logs to copy", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateFilterButtonsUI() {
        val activeColor = Color.parseColor("#374151")
        val inactiveColor = Color.parseColor("#1F2937")

        binding.btnFilterAll.backgroundTintList = ContextCompat.getColorStateList(
            requireContext(),
            if (currentFilter == FILTER_ALL) R.color.vip_gold else android.R.color.transparent
        )
        binding.btnFilterAll.setTextColor(if (currentFilter == FILTER_ALL) Color.BLACK else Color.parseColor("#9CA3AF"))

        binding.btnFilterXhr.backgroundTintList = ContextCompat.getColorStateList(
            requireContext(),
            if (currentFilter == FILTER_XHR) R.color.vip_gold else android.R.color.transparent
        )
        binding.btnFilterXhr.setTextColor(if (currentFilter == FILTER_XHR) Color.BLACK else Color.parseColor("#9CA3AF"))

        binding.btnFilterErrors.backgroundTintList = ContextCompat.getColorStateList(
            requireContext(),
            if (currentFilter == FILTER_ERRORS) R.color.vip_gold else android.R.color.transparent
        )
        binding.btnFilterErrors.setTextColor(if (currentFilter == FILTER_ERRORS) Color.BLACK else Color.parseColor("#9CA3AF"))
    }

    private fun populateNetworkList() {
        val container = binding.layoutNetworkList
        container.removeAllViews()

        val allEntries = DevInspectorManager.networkEntries.toList()
        val total = allEntries.size
        val failed = DevInspectorManager.getFailedNetworkCount()

        binding.tvNetworkSummary.text = "Total Requests: $total | Failed/Errors: $failed"

        val filtered = when (currentFilter) {
            FILTER_XHR -> allEntries.filter { it.isXhr }
            FILTER_ERRORS -> allEntries.filter { it.status == 0 || it.status >= 400 || it.error.isNotEmpty() }
            else -> allEntries
        }

        if (filtered.isEmpty()) {
            val emptyTv = TextView(requireContext()).apply {
                text = if (total == 0) "No network requests recorded yet.\nInteract with website or reload page." else "No requests match the selected filter."
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
            val cardView = createNetworkEntryView(entry, density)
            container.addView(cardView)
        }
    }

    private fun createNetworkEntryView(entry: DevNetworkEntry, density: Float): View {
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

        // Top Row: Method, Status badge, Time, Duration
        val topRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Status badge
        val statusBgColor = when {
            entry.status in 200..299 -> Color.parseColor("#059669") // Green
            entry.status in 300..399 -> Color.parseColor("#2563EB") // Blue
            entry.status in 400..599 -> Color.parseColor("#DC2626") // Red
            else -> Color.parseColor("#DC2626") // Red (failed / 0)
        }
        val statusText = if (entry.status == 0) "FAILED" else entry.status.toString()

        val tvStatus = TextView(ctx).apply {
            text = statusText
            textSize = 10f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setBackgroundColor(statusBgColor)
            val hPad = (6 * density).toInt()
            val vPad = (2 * density).toInt()
            setPadding(hPad, vPad, hPad, vPad)
        }
        topRow.addView(tvStatus)

        // Method
        val tvMethod = TextView(ctx).apply {
            text = entry.method
            textSize = 11f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#38BDF8"))
            val sPad = (6 * density).toInt()
            setPadding(sPad, 0, 0, 0)
        }
        topRow.addView(tvMethod)

        // Type (XHR / Asset)
        val tvType = TextView(ctx).apply {
            text = if (entry.isXhr) "[Fetch/XHR]" else "[Asset]"
            textSize = 10f
            setTextColor(Color.parseColor("#9CA3AF"))
            val sPad = (6 * density).toInt()
            setPadding(sPad, 0, 0, 0)
        }
        topRow.addView(tvType)

        // Spacer
        val spacer = View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
        }
        topRow.addView(spacer)

        // Time & Duration
        val tvMeta = TextView(ctx).apply {
            text = "${entry.time} | ${entry.duration}"
            textSize = 10f
            setTextColor(Color.parseColor("#6B7280"))
        }
        topRow.addView(tvMeta)

        rowLayout.addView(topRow)

        // URL Row
        val tvUrl = TextView(ctx).apply {
            text = entry.url
            textSize = 11f
            setTextColor(Color.parseColor("#E5E7EB"))
            val vPad = (4 * density).toInt()
            setPadding(0, vPad, 0, 0)
            isClickable = true
            isFocusable = true
        }
        rowLayout.addView(tvUrl)

        // Error message if any
        if (entry.error.isNotEmpty()) {
            val tvError = TextView(ctx).apply {
                text = "⚠️ ${entry.error}"
                textSize = 11f
                setTextColor(Color.parseColor("#F87171"))
                setTypeface(typeface, Typeface.BOLD)
                val vPad = (2 * density).toInt()
                setPadding(0, vPad, 0, 0)
            }
            rowLayout.addView(tvError)
        }

        // Tap on row to copy URL
        rowLayout.setOnClickListener {
            val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("URL", entry.url)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(ctx, "Copied URL: ${entry.url.take(40)}...", Toast.LENGTH_SHORT).show()
        }

        return rowLayout
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
