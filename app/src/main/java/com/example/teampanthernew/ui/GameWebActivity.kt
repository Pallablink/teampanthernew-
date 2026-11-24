package com.example.teampanthernew.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.Animation
import android.view.animation.RotateAnimation
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.JavascriptInterface
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.ServiceWorkerClient
import android.webkit.ServiceWorkerController
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.webkit.ServiceWorkerControllerCompat
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import android.graphics.Typeface
import android.view.Gravity
import android.view.KeyEvent
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import com.example.teampanthernew.R
import com.example.teampanthernew.data.AuthPreferenceManager
import com.example.teampanthernew.data.AutoBetRecord
import com.example.teampanthernew.databinding.ActivityGameWebBinding
import com.example.teampanthernew.utils.HistoryItem
import com.example.teampanthernew.utils.Tilt3DHelper
import com.example.teampanthernew.utils.VipSignalEngine
import com.example.teampanthernew.utils.DevInspectorManager
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

class GameWebActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGameWebBinding
    private lateinit var prefManager: AuthPreferenceManager

    private var targetUrl = AuthPreferenceManager.DEFAULT_TARGET_URL

    // Game Mode: 30s, 60s (1m), 180s (3m), 300s (5m)
    private var selectedModeSeconds = 30
    private var selectedModeLabel = "30 SEC"

    // Real Live Period & Countdown Tracking
    private var currentLivePeriodId = ""
    private var currentTimeLeftSec = -1

    // Bot State & Level Management
    private var isAutoBetRunning = false
    private var baseBetAmount = 1.0
    private var nextBetAmount = 1.0
    private var currentLevel = 1
    private var maxLevels = 10
    private var is2xMultiplier = true
    private var stopLossBudget = 2000.0
    private var consecutiveLossStreakAmount = 0.0
    private var sessionProfit = 0.0
    private var sessionWins = 0
    private var sessionLosses = 0
    private var targetAmount = 500.0
    private var selectedStrategy = "AI Neural Predictor"
    private var currentBalance = 0.0

    // Auto-Bet Strict Execution & Level Synchronization Guards
    private val executedBetRecords = ArrayList<AutoBetRecord>()
    private var isBetPlacingInProgress = false
    private var isScreenTouchLocked = false
    private var isBotDispatchedTouch = false
    private var lastLockToastTime = 0L
    private val touchLockSafetyRunnable = Runnable {
        setScreenTouchLock(false)
    }
    private var lastBetPeriodId = ""
    private var lastBetChoice = ""
    private var lastBetStake = 1.0
    private var pendingBetPeriodId = ""
    private var pendingBetChoice = ""
    private var pendingBetStake = 0.0
    private var pendingBetLevel = 1
    private var lastResolvedPeriodId = ""
    private var lastResolvedWasWin = false

    // UI Tab State on BDG WIN
    private var isMyHistoryTabActiveOnSite = false

    // History for AI Signal Engine
    private val historicalItems = mutableListOf<HistoryItem>()
    private val last6History = mutableListOf("B", "S", "B", "B", "S", "S")
    private var currentSignal = "BIG"
    private var currentSignalStrength = 75

    // Floating Bot Capsule Touch Drag State
    private var isTouchOnFloatingCapsule = false
    private var isDraggingFloatingCapsule = false
    private var capsuleTouchDownRawX = 0f
    private var capsuleTouchDownRawY = 0f
    private var capsuleInitialX = 0f
    private var capsuleInitialY = 0f
    private val touchSlop by lazy { ViewConfiguration.get(this).scaledTouchSlop.toFloat() }

    // Timing & Periodic Watchdogs
    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastServerStatusCheckTime = 0L
    private var lastInjectionCheckTime = 0L
    private var lastBalanceSyncTime = 0L

    private val syncTimerRunnable = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()

            // 1. Smoothly decrement countdown
            if (currentTimeLeftSec > 0) {
                currentTimeLeftSec--
            } else if (currentTimeLeftSec == 0) {
                currentTimeLeftSec = selectedModeSeconds
                val cleanPid = currentLivePeriodId.filter { it.isDigit() }
                if (cleanPid.length >= 10) {
                    val nextPid = (cleanPid.toLongOrNull() ?: 0L) + 1L
                    currentLivePeriodId = nextPid.toString()
                    binding.tvPeriodNumber.text = "#${currentLivePeriodId.takeLast(4)}"
                }
                binding.webView.evaluateJavascript("if (typeof pollGameData === 'function') pollGameData();", null)
            } else {
                val cal = Calendar.getInstance()
                val secFromMidnight = cal.get(Calendar.HOUR_OF_DAY) * 3600 + cal.get(Calendar.MINUTE) * 60 + cal.get(Calendar.SECOND)
                val modeSec = selectedModeSeconds.coerceAtLeast(30)
                val secRem = (modeSec - ((secFromMidnight + 1) % modeSec)) % modeSec
                currentTimeLeftSec = if (secRem == 0) modeSec else secRem
            }

            val min = (currentTimeLeftSec.coerceAtLeast(0)) / 60
            val sec = (currentTimeLeftSec.coerceAtLeast(0)) % 60
            val countdownStr = String.format(Locale.US, "%02d:%02d", min, sec)
            binding.tvHudCountdown.text = countdownStr
            binding.tvMiniBotText.text = "PANTHER $countdownStr"

            // Keep Period ID auto-aligned
            if (currentLivePeriodId.isEmpty()) {
                val cal = Calendar.getInstance()
                val secFromMidnight = cal.get(Calendar.HOUR_OF_DAY) * 3600 + cal.get(Calendar.MINUTE) * 60 + cal.get(Calendar.SECOND)
                val modeSec = selectedModeSeconds.coerceAtLeast(30)
                val roundIndex = ((secFromMidnight + 1) / modeSec) + 1
                val sdf = SimpleDateFormat("yyMMdd", Locale.US)
                currentLivePeriodId = String.format(Locale.US, "%s%04d", sdf.format(Date()), roundIndex)
                binding.tvPeriodNumber.text = String.format(Locale.US, "#%04d", roundIndex)
            } else if (currentLivePeriodId.length >= 4) {
                val last4 = currentLivePeriodId.takeLast(4)
                binding.tvPeriodNumber.text = "#$last4"
            }

            // Update Last Round Display strictly from the latest completed round (< current live round)
            updateLastRoundUi()

            

            Log.i("PANTHER_HUD", "PERIOD=${binding.tvPeriodNumber.text} | LAST=${binding.tvLastResult.text} | COUNT=${binding.tvHudCountdown.text} | BAL=${binding.tvUserBalance.text}")

            // Check auto-bet workflow (5s pre-bet lock, target profit guard, execution)
            if (isAutoBetRunning && currentLivePeriodId.isNotEmpty()) {
                val cleanPeriod = currentLivePeriodId.filter { it.isDigit() }
                checkAndExecuteAutoBetWorkflow(cleanPeriod, currentTimeLeftSec)
            }

            // 2b. Automatically dismiss Win/Loss Result Popup right when new round begins (between 26s and 29s)
            if (!isBetPlacingInProgress && currentTimeLeftSec in 26..29) {
                binding.webView.evaluateJavascript("if (typeof window.tigerCheckAndDismissResultPopup === 'function') window.tigerCheckAndDismissResultPopup();", null)
            }

            // 2. Periodically check server app status & user approval (every 12 seconds)
            if (now - lastServerStatusCheckTime > 12000) {
                lastServerStatusCheckTime = now
                checkServerSettingsAndUserStatus(showToast = false)
            }

            // 3. Every 2 seconds: poll live game data (balance, current issue, history)
            if (now - lastBalanceSyncTime > 2000) {
                lastBalanceSyncTime = now
                binding.webView.evaluateJavascript("if (typeof pollGameData === 'function') pollGameData();", null)
            }

            // 4. Every 3 seconds: ensure script is active & healthy inside WebView
            if (now - lastInjectionCheckTime > 3000) {
                lastInjectionCheckTime = now
                binding.webView.evaluateJavascript(
                    "(function() { return (typeof window._bdgAutomationInjected !== 'undefined' && window._bdgAutomationInjected === true); })();"
                ) { result ->
                    if (result == null || result == "false" || result == "null") {
                        injectAutomationScript()
                    }
                }
            }

            mainHandler.postDelayed(this, 1000)
        }
    }

    override fun attachBaseContext(newBase: Context) {
        val overrideConfig = android.content.res.Configuration(newBase.resources.configuration)
        overrideConfig.fontScale = 1.0f
        val context = newBase.createConfigurationContext(overrideConfig)
        super.attachBaseContext(context)
    }

    override fun getResources(): android.content.res.Resources {
        val res = super.getResources()
        val config = res.configuration
        if (config.fontScale != 1.0f) {
            val newConfig = android.content.res.Configuration(config)
            newConfig.fontScale = 1.0f
            return createConfigurationContext(newConfig).resources
        }
        return res
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        if (newConfig.fontScale != 1.0f) {
            newConfig.fontScale = 1.0f
            @Suppress("DEPRECATION")
            resources.updateConfiguration(newConfig, resources.displayMetrics)
        }
        binding.webView.settings.textZoom = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val config = resources.configuration
        if (config.fontScale != 1.0f) {
            config.fontScale = 1.0f
            @Suppress("DEPRECATION")
            resources.updateConfiguration(config, resources.displayMetrics)
        }
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        window.setFlags(
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        )
        binding = ActivityGameWebBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefManager = AuthPreferenceManager(this)
        targetUrl = prefManager.targetWebUrl

        // Restore saved bot parameters
        targetAmount = prefManager.targetProfit
        baseBetAmount = prefManager.firstBetAmount
        maxLevels = prefManager.maxLevels
        is2xMultiplier = prefManager.is2xMultiplier
        selectedStrategy = prefManager.selectedStrategy
        stopLossBudget = prefManager.stopLossBudget

        val savedBalStr = prefManager.lastKnownBalance
        binding.tvUserBalance.text = "₹$savedBalStr"
        currentBalance = savedBalStr.replace(",", "").toDoubleOrNull() ?: 0.0

        nextBetAmount = calculateStakeForLevel(1)

        setupStatusBarInsets()
        setupTopHeader()
        setupFloatingHud()
        setupDevInspector()
        setupWebView()
        setupBackNavigation()
        updateDashboardStatsUI()

        // Load target URL directly inside the in-app WebView engine
        loadTargetUrl()

        mainHandler.post(syncTimerRunnable)

        // Initial check of server status
        checkServerSettingsAndUserStatus(showToast = false)

        // Welcome notification
        binding.root.postDelayed({
            showVipNotification("Welcome to Team Panther", R.drawable.logo_header_vip_robot)
        }, 800)
    }

    /**
     * Option A: Launches the website directly inside genuine Google Chrome Custom Tabs.
     * Guarantees 100% graphics, full lottery & casino games, and zero WebView crashes.
     */
    fun launchChromeCustomTab(url: String = targetUrl) {
        try {
            val colorScheme = CustomTabColorSchemeParams.Builder()
                .setToolbarColor(ContextCompat.getColor(this, R.color.vip_bg_dark))
                .setSecondaryToolbarColor(ContextCompat.getColor(this, R.color.vip_bg_card))
                .setNavigationBarColor(ContextCompat.getColor(this, R.color.vip_bg_dark))
                .build()

            val customTabsIntent = CustomTabsIntent.Builder()
                .setShowTitle(true)
                .setUrlBarHidingEnabled(true)
                .setDefaultColorSchemeParams(colorScheme)
                .setShareState(CustomTabsIntent.SHARE_STATE_OFF)
                .build()

            val chromePackage = "com.android.chrome"
            val isChromeInstalled = try {
                packageManager.getPackageInfo(chromePackage, 0)
                true
            } catch (_: Exception) {
                false
            }
            if (isChromeInstalled) {
                customTabsIntent.intent.setPackage(chromePackage)
            }

            customTabsIntent.launchUrl(this, Uri.parse(url))
        } catch (e: Exception) {
            Log.e("PantherChrome", "Failed to launch Chrome Custom Tabs", e)
            try {
                val fallback = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                startActivity(fallback)
            } catch (ex: Exception) {
                Toast.makeText(this, "Could not open browser: ${ex.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Prevents system status bar from overlapping header.
     */
    private fun setupStatusBarInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            binding.statusBarSpacer.updateLayoutParams {
                height = statusBars.top
            }
            insets
        }
    }

    /**
     * Top Header controls (Back, Refresh, Logout, Re-center).
     */
    private fun setupTopHeader() {
        binding.btnHeaderBack.setOnClickListener {
            handleBackPressAction()
        }

        binding.btnHeaderRefresh.setOnClickListener {
            val rotate = RotateAnimation(
                0f, 360f,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f
            ).apply {
                duration = 500
                repeatCount = 0
            }
            binding.btnHeaderRefresh.startAnimation(rotate)

            binding.webView.reload()
            checkServerSettingsAndUserStatus(showToast = true)
            Toast.makeText(this, "Refreshing Game...", Toast.LENGTH_SHORT).show()
        }

        // Long-click on refresh re-centers the floating panel
        binding.btnHeaderRefresh.setOnLongClickListener {
            resetFloatingPanelPosition(animate = true)
            true
        }

        binding.btnHeaderLogout.setOnClickListener {
            showLogoutConfirmation()
        }

        binding.ivHeaderLogo.setOnClickListener {
            openUserProfile()
        }
        binding.tvHeaderTitle.setOnClickListener {
            openUserProfile()
        }

        Tilt3DHelper.attach3DPressEffect(binding.btnHeaderBack)
        Tilt3DHelper.attach3DPressEffect(binding.btnHeaderRefresh)
        Tilt3DHelper.attach3DPressEffect(binding.btnHeaderLogout)
        Tilt3DHelper.attach3DPressEffect(binding.ivHeaderLogo)
        Tilt3DHelper.attach3DPressEffect(binding.tvHeaderTitle)
    }

    /**
     * Re-centers the floating widget to safe center-top of the screen.
     */
    private fun resetFloatingPanelPosition(animate: Boolean = true) {
        val parent = binding.layoutFloatingBotCapsule.parent as? View ?: return
        val parentW = parent.width.toFloat()
        val widgetW = binding.layoutFloatingBotCapsule.width.toFloat()
        val density = resources.displayMetrics.density

        val defaultX = if (parentW > widgetW && widgetW > 0) {
            ((parentW - widgetW) / 2f).coerceAtLeast(8f * density)
        } else {
            8f * density
        }
        val defaultY = 16f * density

        if (animate) {
            binding.layoutFloatingBotCapsule.animate()
                .x(defaultX)
                .y(defaultY)
                .setDuration(300)
                .start()
        } else {
            binding.layoutFloatingBotCapsule.x = defaultX
            binding.layoutFloatingBotCapsule.y = defaultY
        }
        Toast.makeText(this, "Panel Centered", Toast.LENGTH_SHORT).show()
    }

    /**
     * Clamps the floating widget strictly within visible screen boundaries
     * so it NEVER gets cropped and the minimize button is ALWAYS visible!
     */
    private fun clampFloatingWidgetToBounds(animate: Boolean = true) {
        binding.layoutFloatingBotCapsule.post {
            val parent = binding.layoutFloatingBotCapsule.parent as? View ?: return@post
            val parentW = parent.width.toFloat()
            val parentH = parent.height.toFloat()
            val widgetW = binding.layoutFloatingBotCapsule.width.toFloat()
            val widgetH = binding.layoutFloatingBotCapsule.height.toFloat()

            if (parentW <= 0 || parentH <= 0 || widgetW <= 0 || widgetH <= 0) return@post

            val density = resources.displayMetrics.density
            val marginX = 8f * density
            val marginY = 8f * density

            val minX = marginX
            val maxX = (parentW - widgetW - marginX).coerceAtLeast(marginX)
            val minY = marginY
            val maxY = (parentH - widgetH - marginY).coerceAtLeast(marginY)

            val targetX = binding.layoutFloatingBotCapsule.x.coerceIn(minX, maxX)
            val targetY = binding.layoutFloatingBotCapsule.y.coerceIn(minY, maxY)

            if (binding.layoutFloatingBotCapsule.x != targetX || binding.layoutFloatingBotCapsule.y != targetY) {
                if (animate) {
                    binding.layoutFloatingBotCapsule.animate()
                        .x(targetX)
                        .y(targetY)
                        .setDuration(250)
                        .start()
                } else {
                    binding.layoutFloatingBotCapsule.x = targetX
                    binding.layoutFloatingBotCapsule.y = targetY
                }
            }
        }
    }

    /**
     * Sets up the complete BDG Trade Bot Floating HUD with touch dragging and safe bounds.
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun setupFloatingHud() {
        val density = resources.displayMetrics.density
        binding.layoutFloatingBotCapsule.cameraDistance = 8000 * density

        binding.layoutFloatingBotCapsule.post {
            val parent = binding.layoutFloatingBotCapsule.parent as? View
            if (parent != null && parent.width > 0) {
                val initX = ((parent.width - binding.layoutFloatingBotCapsule.width) / 2f).coerceAtLeast(8f * density)
                binding.layoutFloatingBotCapsule.x = initX
                binding.layoutFloatingBotCapsule.y = 12f * density
                clampFloatingWidgetToBounds(animate = false)
            }
        }

        // Click on mini pill expands the HUD
        binding.cardMiniPill.setOnClickListener {
            expandHud()
        }

        // Direct Touch Drag Listener for expanded HUD panel (top handle and header)
        val panelDragListener = View.OnTouchListener { _, event ->
            val capsule = binding.layoutFloatingBotCapsule
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    isTouchOnFloatingCapsule = true
                    isDraggingFloatingCapsule = false
                    capsuleTouchDownRawX = event.rawX
                    capsuleTouchDownRawY = event.rawY
                    capsuleInitialX = capsule.x
                    capsuleInitialY = capsule.y
                    capsule.parent?.requestDisallowInterceptTouchEvent(true)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - capsuleTouchDownRawX
                    val dy = event.rawY - capsuleTouchDownRawY
                    val dist = Math.hypot(dx.toDouble(), dy.toDouble())
                    if (!isDraggingFloatingCapsule && dist > touchSlop) {
                        isDraggingFloatingCapsule = true
                        capsule.animate().scaleX(1.02f).scaleY(1.02f).setDuration(80).start()
                    }
                    if (isDraggingFloatingCapsule) {
                        val parent = capsule.parent as? View
                        if (parent != null && parent.width > 0 && parent.height > 0) {
                            val density = resources.displayMetrics.density
                            val margin = 8f * density
                            val newX = capsuleInitialX + dx
                            val newY = capsuleInitialY + dy
                            val maxX = (parent.width - capsule.width - margin).coerceAtLeast(margin)
                            val maxY = (parent.height - capsule.height - margin).coerceAtLeast(margin)
                            capsule.x = newX.coerceIn(margin, maxX)
                            capsule.y = newY.coerceIn(margin, maxY)
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isTouchOnFloatingCapsule = false
                    capsule.parent?.requestDisallowInterceptTouchEvent(false)
                    if (isDraggingFloatingCapsule) {
                        isDraggingFloatingCapsule = false
                        capsule.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                        clampFloatingWidgetToBounds(animate = true)
                    }
                    true
                }
                else -> false
            }
        }
        binding.viewExpandedDragHandle.setOnTouchListener(panelDragListener)
        binding.layoutDragHandleContainer.setOnTouchListener(panelDragListener)

        // Long-click to re-center panel from drag handle, container, or mini pill
        val reCenterLongClickListener = View.OnLongClickListener {
            resetFloatingPanelPosition(animate = true)
            true
        }
        binding.viewExpandedDragHandle.setOnLongClickListener(reCenterLongClickListener)
        binding.layoutDragHandleContainer.setOnLongClickListener(reCenterLongClickListener)
        binding.cardMiniPill.setOnLongClickListener(reCenterLongClickListener)

        // Minimize & Action buttons
        binding.btnFloatingMinimize.setOnClickListener {
            minimizeHud()
        }

        binding.btnGameMode.setOnClickListener {
            showGameModePicker()
        }

        binding.btnStopAuto.setOnClickListener {
            toggleAutoBet()
        }
        isAutoBetRunning = false
        binding.btnStopAuto.text = "▶  START AUTO"
        binding.btnStopAuto.setBackgroundResource(R.drawable.bg_bot_btn_running)
        binding.tvMiniBotText.text = "PANTHER • STOPPED"

        binding.btnBetOnce.setOnClickListener {
            triggerBetOnce()
        }

        binding.btnResetStats.setOnClickListener {
            resetSessionStats()
        }

        binding.btnBotSettings.setOnClickListener {
            openBotSettings()
        }

        binding.btnHudCalculator.setOnClickListener {
            openLevelCalculator()
        }

        binding.btnViewAllHistory.setOnClickListener {
            openBetHistory()
        }

        binding.btnSyncBalance.setOnClickListener {
            binding.webView.evaluateJavascript("if (typeof scrapeBalance === 'function') scrapeBalance();", null)
            Toast.makeText(this, "Syncing wallet balance...", Toast.LENGTH_SHORT).show()
        }

        Tilt3DHelper.attach3DPressEffect(binding.btnStopAuto)
        Tilt3DHelper.attach3DPressEffect(binding.btnBetOnce)
        Tilt3DHelper.attach3DPressEffect(binding.btnFloatingMinimize)
        Tilt3DHelper.attach3DPressEffect(binding.btnHudCalculator)
        Tilt3DHelper.attach3DPressEffect(binding.btnBotSettings)
        Tilt3DHelper.attach3DPressEffect(binding.btnResetStats)
        updateLast20HistoryUi()
    }

    private fun minimizeHud() {
        binding.cardExpandedCapsule.visibility = View.GONE
        binding.cardMiniPill.visibility = View.VISIBLE
        binding.cardMiniPill.alpha = 0f
        binding.cardMiniPill.animate().alpha(1f).setDuration(150).start()
        clampFloatingWidgetToBounds(animate = true)
    }

    private fun expandHud() {
        binding.cardMiniPill.visibility = View.GONE
        binding.cardExpandedCapsule.visibility = View.VISIBLE
        binding.cardExpandedCapsule.alpha = 0f
        binding.cardExpandedCapsule.animate().alpha(1f).setDuration(150).start()
        clampFloatingWidgetToBounds(animate = true)
    }

    private fun setScreenTouchLock(locked: Boolean) {
        runOnUiThread {
            if (isScreenTouchLocked == locked) return@runOnUiThread
            isScreenTouchLocked = locked
            mainHandler.removeCallbacks(touchLockSafetyRunnable)

            if (locked) {
                // Safety timeout: automatically unlock after 12 seconds
                mainHandler.postDelayed(touchLockSafetyRunnable, 12000)
                try { binding.viewTouchBlocker.visibility = View.VISIBLE } catch(_: Exception) {}
                binding.tvMiniBotText.text = "PANTHER • 🔒 BETTING..."
            } else {
                try { binding.viewTouchBlocker.visibility = View.GONE } catch(_: Exception) {}
                if (isAutoBetRunning) {
                    binding.tvMiniBotText.text = "PANTHER • AUTO RUNNING"
                } else {
                    binding.tvMiniBotText.text = "PANTHER • STOPPED"
                }
            }
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (isScreenTouchLocked && !isBotDispatchedTouch) {
            if (ev.action == MotionEvent.ACTION_DOWN) {
                val now = SystemClock.uptimeMillis()
                if (now - lastLockToastTime > 1500) {
                    lastLockToastTime = now
                    showVipNotification("🔒 Auto robot is taking bet, please don't click on the screen.", R.drawable.icon_radar_3d)
                }
            }
            return true
        } 
        if (::binding.isInitialized) {
            val capsule = binding.layoutFloatingBotCapsule
            if (capsule.visibility == View.VISIBLE) {
                val loc = IntArray(2)
                capsule.getLocationOnScreen(loc)
                val left = loc[0].toFloat()
                val top = loc[1].toFloat()
                val right = left + capsule.width
                val bottom = top + capsule.height

                when (ev.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        if (ev.rawX in left..right && ev.rawY in top..bottom) {
                            isTouchOnFloatingCapsule = true
                            isDraggingFloatingCapsule = false
                            capsuleTouchDownRawX = ev.rawX
                            capsuleTouchDownRawY = ev.rawY
                            capsuleInitialX = capsule.x
                            capsuleInitialY = capsule.y
                            capsule.parent?.requestDisallowInterceptTouchEvent(true)
                        }
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (isTouchOnFloatingCapsule) {
                            val dx = ev.rawX - capsuleTouchDownRawX
                            val dy = ev.rawY - capsuleTouchDownRawY
                            val dist = Math.hypot(dx.toDouble(), dy.toDouble())

                            if (!isDraggingFloatingCapsule && dist > touchSlop) {
                                isDraggingFloatingCapsule = true
                                // Cancel child button pressed / ripple state
                                val cancelEvent = MotionEvent.obtain(ev)
                                cancelEvent.action = MotionEvent.ACTION_CANCEL
                                super.dispatchTouchEvent(cancelEvent)
                                cancelEvent.recycle()
                                capsule.animate().scaleX(1.02f).scaleY(1.02f).setDuration(80).start()
                            }

                            if (isDraggingFloatingCapsule) {
                                val parent = capsule.parent as? View
                                if (parent != null && parent.width > 0 && parent.height > 0) {
                                    val density = resources.displayMetrics.density
                                    val margin = 8f * density
                                    val newX = capsuleInitialX + dx
                                    val newY = capsuleInitialY + dy
                                    val maxX = (parent.width - capsule.width - margin).coerceAtLeast(margin)
                                    val maxY = (parent.height - capsule.height - margin).coerceAtLeast(margin)
                                    capsule.x = newX.coerceIn(margin, maxX)
                                    capsule.y = newY.coerceIn(margin, maxY)
                                }
                                return true
                            }
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        if (isTouchOnFloatingCapsule) {
                            isTouchOnFloatingCapsule = false
                            capsule.parent?.requestDisallowInterceptTouchEvent(false)
                            if (isDraggingFloatingCapsule) {
                                isDraggingFloatingCapsule = false
                                capsule.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                                clampFloatingWidgetToBounds(animate = true)
                                return true
                            }
                        }
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        if (isTouchOnFloatingCapsule) {
                            isTouchOnFloatingCapsule = false
                            isDraggingFloatingCapsule = false
                            capsule.parent?.requestDisallowInterceptTouchEvent(false)
                            capsule.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                            clampFloatingWidgetToBounds(animate = true)
                        }
                    }
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    /**
     * Checks if user is permitted to trade based on Admin approval.
     */
    private fun canStartTrading(): Boolean {
        if (prefManager.appStatus != "active") {
            showAppInactiveNotice()
            return false
        }
        if (prefManager.userStatus == "pending") {
            showPendingApprovalDialog()
            return false
        }
        if (prefManager.userStatus == "banned" || prefManager.userStatus == "suspended") {
            showBannedDialog()
            return false
        }
        return true
    }

    private fun showPendingApprovalDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_vip_exit, null)
        val tvExitTitle = dialogView.findViewById<TextView>(R.id.tvExitTitle)
        val tvExitMessage = dialogView.findViewById<TextView>(R.id.tvExitMessage)
        val btnStay = dialogView.findViewById<View>(R.id.btnStay)
        val btnExit = dialogView.findViewById<View>(R.id.btnExit)

        tvExitTitle.text = "APPROVAL PENDING"
        tvExitMessage.text = "Your account is pending review by the Administrator.\n\nBot trading automation is disabled until approved."
        (btnStay as? TextView)?.text = "CLOSE"
        (btnExit as? TextView)?.text = "CHECK STATUS"

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnStay.setOnClickListener { dialog.dismiss() }
        btnExit.setOnClickListener {
            dialog.dismiss()
            checkServerSettingsAndUserStatus(showToast = true)
        }
        dialog.show()
    }

    private fun showAppInactiveNotice() {
        val msg = if (prefManager.maintenanceMessage.isNotEmpty()) prefManager.maintenanceMessage
        else "TeamPanther is undergoing scheduled maintenance. Bot automation is temporarily paused."

        AlertDialog.Builder(this)
            .setTitle("MAINTENANCE MODE")
            .setMessage(msg)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showBannedDialog() {
        AlertDialog.Builder(this)
            .setTitle("ACCOUNT SUSPENDED")
            .setMessage("Your account has been suspended or banned by administrator.")
            .setPositiveButton("CONTACT SUPPORT") { _, _ ->
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/teampanther"))
                startActivity(intent)
            }
            .setNegativeButton("EXIT", null)
            .show()
    }

    /**
     * Queries PHP backend for dynamic settings and user account status.
     */
    private fun checkServerSettingsAndUserStatus(showToast: Boolean = false) {
        val baseUrl = prefManager.apiBaseUrl
        var urlStr = baseUrl + "get_settings.php?user_id=${prefManager.userId}"

        thread {
            try {
                fun openConn(u: String): HttpURLConnection {
                    val conn = URL(u).openConnection() as HttpURLConnection
                    if (conn is HttpsURLConnection) {
                        conn.hostnameVerifier = HostnameVerifier { hostname, session ->
                            hostname == "panther-team.xyz.ci" || HttpsURLConnection.getDefaultHostnameVerifier().verify(hostname, session)
                        }
                    }
                    conn.connectTimeout = 5000
                    conn.readTimeout = 5000
                    conn.requestMethod = "GET"
                    return conn
                }

                var conn: HttpURLConnection
                try {
                    conn = openConn(urlStr)
                    conn.responseCode
                } catch (sslEx: SSLException) {
                    if (urlStr.startsWith("https://")) {
                        urlStr = urlStr.replace("https://", "http://")
                        conn = openConn(urlStr)
                    } else {
                        throw sslEx
                    }
                }

                if (conn.responseCode == 200) {
                    val resp = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(resp)
                    if (json.optBoolean("success", false)) {
                        val data = json.optJSONObject("data")
                        if (data != null) {
                            val appStatus = data.optString("app_status", "active")
                            val maintMsg = data.optString("maintenance_message", "")
                            val userStatus = data.optString("user_status", "active")
                            val targetWebUrl = data.optString("target_web_url", "")

                            runOnUiThread {
                                prefManager.appStatus = appStatus
                                prefManager.maintenanceMessage = maintMsg
                                prefManager.userStatus = userStatus

                                if (targetWebUrl.isNotEmpty() && targetWebUrl != targetUrl &&
                                    targetWebUrl.startsWith("http") && !targetWebUrl.contains("bdg9", ignoreCase = true)) {
                                    targetUrl = targetWebUrl
                                    prefManager.targetWebUrl = targetUrl
                                }

                                if (showToast) {
                                    Toast.makeText(this@GameWebActivity, "Status: $userStatus | App: $appStatus", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    /**
     * Strictly update Last Round display from the latest completed round (< current live round)
     * e.g. If live period is #1580, LAST displays #1579 with its actual winning number and BIG/SMALL outcome.
     */
    private fun updateLastRoundUi() {
        val curLiveNum = currentLivePeriodId.filter { it.isDigit() }.toLongOrNull() ?: 0L
        val latest = synchronized(historicalItems) {
            historicalItems.firstOrNull {
                val pNum = it.periodId.filter { ch -> ch.isDigit() }.toLongOrNull() ?: 0L
                it.periodId.length >= 10 && (curLiveNum == 0L || pNum < curLiveNum)
            }
        }
        if (latest != null) {
            val p4 = latest.periodId.takeLast(4)
            val choice = latest.outcome.uppercase()
            binding.tvLastResult.text = "LAST #$p4: ${latest.number} $choice"
        } else if (currentLivePeriodId.length >= 4) {
            val curNum = currentLivePeriodId.takeLast(4).toLongOrNull()
            if (curNum != null && curNum > 1) {
                val lastRoundStr = String.format(Locale.US, "%04d", curNum - 1)
                binding.tvLastResult.text = "LAST #$lastRoundStr: WAITING..."
            } else {
                binding.tvLastResult.text = "LAST ROUND --"
            }
        }
    }

    /**
     * Core handler called in real-time whenever JS intercepts period and countdown from KWG API.
     */
    private fun handlePeriodDetected(periodId: String, timeLeftSec: Int, modeName: String) {
        val cleanPeriod = periodId.filter { it.isDigit() }
        if (cleanPeriod.isEmpty() || cleanPeriod.length < 10) return

        val newNum = cleanPeriod.toLongOrNull() ?: 0L
        val curNum = currentLivePeriodId.filter { it.isDigit() }.toLongOrNull() ?: 0L

        // MONOTONIC RULE: Live period only moves forward! An older history row can NEVER downgrade live round.
        if (curNum > 0L && newNum < curNum) {
            return
        }

        if (newNum > curNum || currentLivePeriodId.isEmpty()) {
            currentLivePeriodId = cleanPeriod
            val last4 = cleanPeriod.takeLast(4)
            binding.tvPeriodNumber.text = "#$last4"
            updateLastRoundUi()
        }

        if (timeLeftSec in 0..selectedModeSeconds) {
            currentTimeLeftSec = timeLeftSec
            val min = currentTimeLeftSec / 60
            val sec = currentTimeLeftSec % 60
            val countdownStr = String.format(Locale.US, "%02d:%02d", min, sec)
            binding.tvHudCountdown.text = countdownStr
            binding.tvMiniBotText.text = "PANTHER $countdownStr"
        }

        // 2. Resolve pending bet if result is available
        checkAndResolvePendingBet()

        // 3. Auto-Bet Workflow & Strict Safety Guards
        checkAndExecuteAutoBetWorkflow(cleanPeriod, timeLeftSec)
    }

    private var lastPreLockPeriodId = ""

    private fun checkTargetProfitAndStop(): Boolean {
        if (targetAmount > 0.0 && sessionProfit >= targetAmount) {
            if (isAutoBetRunning) {
                isAutoBetRunning = false
                isBetPlacingInProgress = false
                setScreenTouchLock(false)
                binding.btnStopAuto.text = "▶  START AUTO"
                binding.btnStopAuto.setBackgroundResource(R.drawable.bg_bot_btn_running)
                binding.tvMiniBotText.text = "PANTHER • TARGET HIT"
                showVipNotification("🎉 Target Profit (₹${targetAmount.toInt()}) Reached! Bot Auto-Stopped.", R.drawable.ic_check_circle)
            }
            return true
        }
        return false
    }

    private fun checkMaxLevelsAndStop(): Boolean {
        if (maxLevels > 0 && currentLevel > maxLevels) {
            if (isAutoBetRunning) {
                isAutoBetRunning = false
                isBetPlacingInProgress = false
                setScreenTouchLock(false)
                binding.btnStopAuto.text = "▶  START AUTO"
                binding.btnStopAuto.setBackgroundResource(R.drawable.bg_bot_btn_running)
                binding.tvMiniBotText.text = "PANTHER • MAX LVL REACHED"
                showVipNotification("Max Level $maxLevels Reached. Bot Auto-Stopped.", R.drawable.icon_radar_3d)
            }
            return true
        }
        return false
    }

    private fun checkStopLossAndStop(): Boolean {
        if (stopLossBudget > 0.0) {
            val netLoss = if (sessionProfit < 0) Math.abs(sessionProfit) else 0.0
            val budgetHit = netLoss >= stopLossBudget || consecutiveLossStreakAmount >= stopLossBudget
            if (budgetHit) {
                if (isAutoBetRunning) {
                    isAutoBetRunning = false
                    isBetPlacingInProgress = false
                    setScreenTouchLock(false)
                    binding.btnStopAuto.text = "▶  START AUTO"
                    binding.btnStopAuto.setBackgroundResource(R.drawable.bg_bot_btn_running)
                    binding.tvMiniBotText.text = "PANTHER • STOP LOSS"
                    showVipNotification("Stop Loss Budget (₹${stopLossBudget.toInt()}) Reached! Bot Stopped.", R.drawable.icon_radar_3d)
                }
                return true
            }
        }
        return false
    }

    private fun checkAndExecuteAutoBetWorkflow(cleanPeriod: String, timeLeftSec: Int) {
        if (!isAutoBetRunning) return
        if (cleanPeriod.isEmpty() || cleanPeriod.length < 10) return

        // 1. Strict Stop Safety Guards
        if (checkTargetProfitAndStop()) return
        if (checkMaxLevelsAndStop()) return
        if (checkStopLossAndStop()) return
        if (!canStartTrading()) return

        // Guard 1: Never place more than 1 bet on the same period!
        if (lastBetPeriodId == cleanPeriod || (lastBetPeriodId.isNotEmpty() && cleanPeriod.endsWith(lastBetPeriodId))) return
        if (isBetPlacingInProgress) return

        // Guard 2: Level Synchronization Gate:
        // Wait for previous round result to resolve before placing bet on new round
        val activePending = pendingBetPeriodId.filter { it.isDigit() }
        if (activePending.isNotEmpty() && activePending != cleanPeriod &&
            !activePending.endsWith(cleanPeriod) && !cleanPeriod.endsWith(activePending)) {
            return
        }

        // USER REQUIREMENT:
        // "बेट लेने के 5 सेकंड पहले ही स्क्रीन को लॉक कर दो।
        // फिर बेट लेने के बाद ही, बेट लेने के 3 सेकंड बाद वो, मतलब यूजर्स क्लिक कर पाए।
        // उसके पहले बोल दो कि 'Auto robot is taking bet, please don't click on the screen.'
        // इसीलिए और लॉक कर दो 5 सेकंड पहले ही। बेट लेने के 3 सेकंड बाद ही खोल देना."

        // Phase 1: Pre-bet Lock (5 seconds before taking bet)
        // In 30s Win Go: Pre-lock at 21..25s (Target bet is at 8..20s, optimal at 18s)
        if (timeLeftSec in 21..25) {
            if (lastPreLockPeriodId != cleanPeriod && !isScreenTouchLocked) {
                lastPreLockPeriodId = cleanPeriod
                setScreenTouchLock(true)
                showVipNotification("🔒 Auto robot is taking bet, please don't click on the screen.", R.drawable.icon_radar_3d)
                binding.tvMiniBotText.text = "PANTHER • 🔒 BET IN 5s..."
            }
            return
        }

        // Phase 2: Bet Placement (Target window: between 8s and 20s, bets lock at 5s)
        if (timeLeftSec in 8..20) {
            if (!isScreenTouchLocked) {
                setScreenTouchLock(true)
                showVipNotification("🔒 Auto robot is taking bet, please don't click on the screen.", R.drawable.icon_radar_3d)
            }
            executeAutoBet(cleanPeriod, currentSignal, nextBetAmount)
        }
    }

    /**
     * Resolves pending bet by cross-checking against scraped official Game History or My History orders.
     */
    private fun checkAndResolvePendingBet(): Boolean {
        val targetPid = pendingBetPeriodId.filter { it.isDigit() }
        if (targetPid.isEmpty()) return false

        // 1. Safety check: NEVER resolve if the bet period is still active and counting down
        val curP = currentLivePeriodId.filter { it.isDigit() }
        val isStillActive = curP.isNotEmpty() &&
                (targetPid == curP || (targetPid.length >= 6 && curP.length >= 6 && (targetPid.endsWith(curP) || curP.endsWith(targetPid)))) &&
                currentTimeLeftSec > 1
        if (isStillActive) {
            return false // Active period, still counting down
        }

        // 2. Check historical items already scraped
        val matchedItem = synchronized(historicalItems) {
            historicalItems.firstOrNull {
                val p = it.periodId.filter { ch -> ch.isDigit() }
                p.isNotEmpty() && (p == targetPid || (p.length >= 6 && targetPid.length >= 6 && (p.endsWith(targetPid) || targetPid.endsWith(p))))
            }
        }

        if (matchedItem != null && pendingBetChoice.isNotEmpty()) {
            val won = when (pendingBetChoice.uppercase()) {
                "RED" -> matchedItem.number in listOf(0, 2, 4, 6, 8) || matchedItem.outcome.equals("RED", ignoreCase = true)
                "GREEN" -> matchedItem.number in listOf(1, 3, 5, 7, 9) || matchedItem.outcome.equals("GREEN", ignoreCase = true)
                "BIG" -> matchedItem.outcome.equals("BIG", ignoreCase = true) || matchedItem.number in 5..9
                "SMALL" -> matchedItem.outcome.equals("SMALL", ignoreCase = true) || matchedItem.number in 0..4
                else -> matchedItem.outcome.equals(pendingBetChoice, ignoreCase = true)
            }
            resolveBetResult(matchedItem.periodId, won, if (won) pendingBetStake * 0.92 else 0.0)
            return true
        }

        // 3. Actively trigger JS reconciliation on BOTH My History cards AND Game History table
        binding.webView.evaluateJavascript(
            "window._tigerLastBetPeriod = '$targetPid'; " +
            "if (typeof scrapeGameResults === 'function') scrapeGameResults('$currentLivePeriodId', $currentTimeLeftSec); " +
            "if (typeof scrapeGameHistoryTable === 'function') scrapeGameHistoryTable('$currentLivePeriodId'); " +
            "if (typeof scrapeMyHistoryList === 'function') scrapeMyHistoryList();",
            null
        )
        return false
    }

    /**
     * Resolves an executed bet with confirmed WIN or LOSS.
     */
    private fun resolveBetResult(periodId: String, isWin: Boolean, profitAmt: Double) {
        val cleanPid = periodId.filter { it.isDigit() }
        if (cleanPid.isEmpty() || cleanPid.length < 10) return
        if (lastResolvedPeriodId == cleanPid) return

        // SAFETY 1: NEVER resolve if the round is currently live and counting down!
        val curP = currentLivePeriodId.filter { it.isDigit() }
        if (curP.isNotEmpty() && (curP == cleanPid || (curP.length >= 6 && cleanPid.length >= 6 && (curP.endsWith(cleanPid) || cleanPid.endsWith(curP)))) && currentTimeLeftSec > 2) {
            // Bet round is still actively counting down; result has not been drawn yet!
            return
        }

        // SAFETY 2: Check matching target bet record
        val targetRecord = executedBetRecords.firstOrNull {
            val p = it.periodId.filter { ch -> ch.isDigit() }
            p == cleanPid || (p.length >= 6 && cleanPid.length >= 6 && (p.endsWith(cleanPid) || cleanPid.endsWith(p)))
        }

        // If no matching pending bet exists and targetRecord is null, skip old past records!
        if (targetRecord == null && pendingBetPeriodId.isEmpty()) {
            return
        }

        // If this record was already settled as WIN or LOSS, don't re-settle!
        if (targetRecord != null && (targetRecord.status == "WIN" || targetRecord.status == "LOSS")) {
            return
        }

        lastResolvedPeriodId = cleanPid
        lastResolvedWasWin = isWin
        if (pendingBetChoice.isNotEmpty()) {
            lastBetChoice = pendingBetChoice
        }

        val resolvedStake = targetRecord?.stake ?: (if (pendingBetStake > 0) pendingBetStake else lastBetStake).coerceAtLeast(1.0)
        lastBetStake = resolvedStake
        pendingBetPeriodId = ""
        binding.webView.evaluateJavascript("window._tigerLastBetPeriod = '';", null)

        val resolvedProfit = if (isWin) {
            if (profitAmt > 0) profitAmt else (resolvedStake * 0.92)
        } else {
            -resolvedStake
        }

        if (targetRecord != null) {
            targetRecord.status = if (isWin) "WIN" else "LOSS"
            targetRecord.profit = resolvedProfit
        }

        if (isWin) {
            sessionWins++
            sessionProfit += resolvedProfit
            consecutiveLossStreakAmount = 0.0

            // RESET TO LEVEL 1 ON WIN!
            currentLevel = 1
            nextBetAmount = calculateStakeForLevel(1)

            binding.tvLastRoundWinBanner.text = "LAST #${cleanPid.takeLast(4)} -> WIN +₹${String.format(Locale.US, "%.2f", resolvedProfit)}"
            binding.tvLastRoundWinBanner.setTextColor(ContextCompat.getColor(this, R.color.vip_success))
            showVipNotification("WIN! +₹${String.format(Locale.US, "%.2f", resolvedProfit)} (LVL 1 Reset)", R.drawable.ic_check_circle)

            checkTargetProfitAndStop()
        } else {
            sessionLosses++
            sessionProfit += resolvedProfit
            consecutiveLossStreakAmount += resolvedStake

            // ADVANCE TO NEXT MARTINGALE LEVEL
            currentLevel++
            nextBetAmount = calculateStakeForLevel(currentLevel)

            binding.tvLastRoundWinBanner.text = "LAST #${cleanPid.takeLast(4)} -> LOSS -₹${String.format(Locale.US, "%.2f", resolvedStake)}"
            binding.tvLastRoundWinBanner.setTextColor(ContextCompat.getColor(this, R.color.vip_error))
            showVipNotification("LOSS -₹${resolvedStake.toInt()} -> Next LVL $currentLevel", R.drawable.icon_radar_3d)

            checkMaxLevelsAndStop()
            checkStopLossAndStop()
        }

        recalculateProfitFromRecords()
        updateDashboardStatsUI()
        isBetPlacingInProgress = false
        pendingBetChoice = ""
        binding.webView.evaluateJavascript("if (typeof scrapeBalance === 'function') scrapeBalance();", null)
    }

    /**
     * Overturns an incorrect resolution when official Game History table delivers the true outcome.
     */
    private fun overturnBetResult(cleanPid: String, trueWin: Boolean, trueOutcome: String, ballNum: Int) {
        val targetRecord = executedBetRecords.firstOrNull {
            val p = it.periodId.filter { ch -> ch.isDigit() }
            p == cleanPid || (p.length >= 6 && cleanPid.length >= 6 && (p.endsWith(cleanPid) || cleanPid.endsWith(p)))
        }
        val stake = targetRecord?.stake ?: lastBetStake.coerceAtLeast(1.0)

        if (trueWin && !lastResolvedWasWin) {
            // Was mistakenly recorded as LOSS -> Correct to WIN
            sessionLosses = (sessionLosses - 1).coerceAtLeast(0)
            sessionWins++
            val winProfit = stake * 0.92
            sessionProfit += (stake + winProfit) // revert -stake + add winProfit
            consecutiveLossStreakAmount = (consecutiveLossStreakAmount - stake).coerceAtLeast(0.0)

            // Reset level to 1 on WIN!
            currentLevel = 1
            nextBetAmount = calculateStakeForLevel(1)

            lastResolvedWasWin = true
            targetRecord?.status = "WIN"
            targetRecord?.profit = winProfit

            binding.tvLastRoundWinBanner.text = "LAST #${cleanPid.takeLast(4)} -> WIN +₹${String.format(Locale.US, "%.2f", winProfit)}"
            binding.tvLastRoundWinBanner.setTextColor(ContextCompat.getColor(this, R.color.vip_success))
            showVipNotification("Correction: WIN! (LVL 1 Reset)", R.drawable.ic_check_circle)
            checkTargetProfitAndStop()
        } else if (!trueWin && lastResolvedWasWin) {
            // Was mistakenly recorded as WIN -> Correct to LOSS
            sessionWins = (sessionWins - 1).coerceAtLeast(0)
            sessionLosses++
            val prevProfit = targetRecord?.profit ?: (stake * 0.92)
            sessionProfit -= (prevProfit + stake) // revert win profit + apply loss of stake
            consecutiveLossStreakAmount += stake

            // Advance level to next Martingale level!
            currentLevel++
            nextBetAmount = calculateStakeForLevel(currentLevel)

            lastResolvedWasWin = false
            targetRecord?.status = "LOSS"
            targetRecord?.profit = -stake

            binding.tvLastRoundWinBanner.text = "LAST #${cleanPid.takeLast(4)} -> LOSS -₹${String.format(Locale.US, "%.2f", stake)}"
            binding.tvLastRoundWinBanner.setTextColor(ContextCompat.getColor(this, R.color.vip_error))
            showVipNotification("Correction: LOSS -₹${stake.toInt()} -> Next LVL $currentLevel", R.drawable.icon_radar_3d)

            checkMaxLevelsAndStop()
            checkStopLossAndStop()
        }

        recalculateProfitFromRecords()
        updateDashboardStatsUI()
        updateLast20HistoryUi()
    }

    /**
     * Recalculates total session wins, losses, and net profit strictly from recorded bet items.
     */
    private fun recalculateProfitFromRecords() {
        var wins = 0
        var losses = 0
        var profit = 0.0
        synchronized(executedBetRecords) {
            for (rec in executedBetRecords) {
                if (rec.status.equals("WIN", ignoreCase = true)) {
                    wins++
                    profit += if (rec.profit > 0) rec.profit else (rec.stake * 0.92)
                } else if (rec.status.equals("LOSS", ignoreCase = true)) {
                    losses++
                    profit += if (rec.profit != 0.0) rec.profit else -rec.stake
                }
            }
        }
        sessionWins = wins
        sessionLosses = losses
        sessionProfit = Math.round(profit * 100.0) / 100.0
        checkTargetProfitAndStop()
        checkMaxLevelsAndStop()
        checkStopLossAndStop()
    }

    private var currentBetRetryCount = 0

    /**
     * Executes Auto-Bet via JavaScript DOM injection (Exact TigerAutoRobot mechanism).
     */
    private fun executeAutoBet(cleanPeriod: String, choice: String, stake: Double, isRetry: Boolean = false) {
        if (!isRetry) {
            if (lastBetPeriodId == cleanPeriod || (lastBetPeriodId.isNotEmpty() && cleanPeriod.endsWith(lastBetPeriodId))) return
            currentBetRetryCount = 0
        } else {
            if (currentBetRetryCount >= 2) {
                Log.w("PantherBet", "Max retries (2) reached for period $cleanPeriod. Skipping.")
                return
            }
            currentBetRetryCount++
        }

        // Strict Stop Safety Guards inside executeAutoBet
        if (checkTargetProfitAndStop()) return
        if (checkMaxLevelsAndStop()) return
        if (checkStopLossAndStop()) return
        if (!canStartTrading()) return
        if (choice.isEmpty()) return

        isBetPlacingInProgress = true
        setScreenTouchLock(true)
        binding.webView.evaluateJavascript("if (typeof window.tigerCheckAndDismissResultPopup === 'function') window.tigerCheckAndDismissResultPopup();", null)
        lastBetPeriodId = cleanPeriod
        lastBetChoice = choice
        lastBetStake = stake
        pendingBetPeriodId = cleanPeriod
        pendingBetChoice = choice
        pendingBetStake = stake
        pendingBetLevel = currentLevel

        if (!isRetry) {
            val timeFmt = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
            executedBetRecords.add(
                0,
                AutoBetRecord(
                    periodId = cleanPeriod,
                    choice = choice,
                    stake = stake,
                    status = "PENDING",
                    level = currentLevel,
                    timestamp = timeFmt
                )
            )
        }

        val safeStake = stake.toInt().coerceAtLeast(1)
        val msg = if (isRetry) "Retrying: ₹$safeStake on $choice (Attempt ${currentBetRetryCount + 1})"
                  else "Placing: ₹$safeStake on $choice (LVL $currentLevel)"
        showVipNotification(msg, R.drawable.icon_radar_3d)

        // Inject JS execution to click the Big/Small button and enter dynamic-level quantity
        // Note: HUD is NEVER minimized automatically! It stays completely stable and visible.
        val script = "window._tigerLastBetPeriod = '$cleanPeriod'; if (typeof window.tigerExecuteBet === 'function') { window.tigerExecuteBet('$choice', $safeStake, $isRetry); }"
        binding.webView.evaluateJavascript(script, null)
    }

    private fun handleBetPlacedSuccess(choice: String, amount: Double) {
        isBetPlacingInProgress = false
        currentBetRetryCount = 0
        showVipNotification("✓ Bet Verified: ₹${amount.toInt()} on $choice • Unlocking in 3s...", R.drawable.icon_radar_3d)
        if (amount > 0 && currentBalance >= amount) {
            currentBalance -= amount
            prefManager.lastKnownBalance = String.format(Locale.US, "%.2f", currentBalance)
            binding.tvUserBalance.text = "₹${prefManager.lastKnownBalance}"
        }
        binding.webView.evaluateJavascript("if (typeof scrapeBalance === 'function') scrapeBalance();", null)

        // USER REQUIREMENT: Unlock screen 3 seconds after bet placement!
        mainHandler.removeCallbacks(touchLockSafetyRunnable)
        mainHandler.postDelayed({
            setScreenTouchLock(false)
            showVipNotification("✓ Screen Unlocked", R.drawable.icon_radar_3d)
        }, 3000)
    }

    private fun handleBetAttemptFailed(reason: String) {
        Log.w("PantherBet", "Bet attempt failed: $reason. Time left: $currentTimeLeftSec, Retries: $currentBetRetryCount")
        dismissKeyboardNative()

        if (currentTimeLeftSec >= 7 && isAutoBetRunning && currentBetRetryCount < 2 && pendingBetPeriodId.isNotEmpty()) {
            showVipNotification("Bet didn't register ($reason). Retrying...", R.drawable.icon_radar_3d)
            binding.webView.postDelayed({
                if (currentTimeLeftSec >= 6 && isAutoBetRunning && pendingBetPeriodId.isNotEmpty()) {
                    executeAutoBet(pendingBetPeriodId, pendingBetChoice, pendingBetStake, isRetry = true)
                }
            }, 350)
        } else {
            isBetPlacingInProgress = false
            mainHandler.postDelayed({
                setScreenTouchLock(false)
            }, 1000)
            val failedPeriod = pendingBetPeriodId
            pendingBetPeriodId = ""
            if (currentTimeLeftSec < 7) {
                showVipNotification("Round locked (<7s). Bet skipped safely.", R.drawable.icon_radar_3d)
            } else {
                showVipNotification("Bet failed: $reason", R.drawable.icon_radar_3d)
            }
            synchronized(executedBetRecords) {
                for (rec in executedBetRecords) {
                    if (rec.periodId == failedPeriod && rec.status == "PENDING") {
                        rec.status = "SKIPPED"
                        break
                    }
                }
            }
        }
    }

    private fun handleScrapedBalance(balanceStr: String) {
        val clean = balanceStr.replace(",", "").trim()
        val num = clean.toDoubleOrNull()
        if (num != null && num >= 0) {
            currentBalance = num
            prefManager.lastKnownBalance = String.format(Locale.US, "%.2f", num)
            binding.tvUserBalance.text = "₹${prefManager.lastKnownBalance}"
        }
    }

    private fun handleGameResult(periodId: String, resultChoice: String, resultColor: String, ballNum: Int) {
        val cleanPid = periodId.filter { it.isDigit() }
        if (cleanPid.isEmpty() || cleanPid.length < 10) return

        val curPid = currentLivePeriodId.filter { it.isDigit() }
        val curPidNum = curPid.toLongOrNull() ?: 0L
        val resPidNum = cleanPid.toLongOrNull() ?: 0L
        if (curPidNum > 0 && resPidNum >= curPidNum) {
            return
        }

        synchronized(historicalItems) {
            if (historicalItems.none { it.periodId == cleanPid }) {
                historicalItems.add(HistoryItem(number = ballNum, outcome = resultChoice, periodId = cleanPid))
                historicalItems.sortByDescending { it.periodId.filter { ch -> ch.isDigit() }.toLongOrNull() ?: 0L }
                while (historicalItems.size > 50) historicalItems.removeAt(historicalItems.lastIndex)
            }
        }

        // Strictly update Last Round display from the latest completed round
        updateLastRoundUi()

        val top6 = synchronized(historicalItems) { historicalItems.take(6) }
        last6History.clear()
        for (item in top6.reversed()) {
            last6History.add(if (item.outcome.equals("BIG", ignoreCase = true)) "B" else "S")
        }
        updateLast6Visuals()

        recalculateAiSignal()

        // Match against active bet or recently placed/resolved bet
        val targetPid = if (pendingBetPeriodId.isNotEmpty()) pendingBetPeriodId.filter { it.isDigit() } else lastBetPeriodId.filter { it.isDigit() }
        val isMatch = targetPid.isNotEmpty() && (cleanPid == targetPid || (cleanPid.length >= 6 && targetPid.length >= 6 && (cleanPid.endsWith(targetPid) || targetPid.endsWith(cleanPid))))

        if (isMatch) {
            val choiceToResolve = if (pendingBetChoice.isNotEmpty()) pendingBetChoice else lastBetChoice
            if (choiceToResolve.isNotEmpty()) {
                val won = when (choiceToResolve.uppercase()) {
                    "RED" -> resultColor.contains("RED", ignoreCase = true) || ballNum in listOf(0, 2, 4, 6, 8)
                    "GREEN" -> resultColor.contains("GREEN", ignoreCase = true) || ballNum in listOf(1, 3, 5, 7, 9)
                    "BIG" -> resultChoice.equals("BIG", ignoreCase = true) || ballNum in 5..9
                    "SMALL" -> resultChoice.equals("SMALL", ignoreCase = true) || ballNum in 0..4
                    else -> resultChoice.equals(choiceToResolve, ignoreCase = true)
                }
                if (lastResolvedPeriodId == cleanPid) {
                    if (lastResolvedWasWin != won) {
                        overturnBetResult(cleanPid, won, choiceToResolve, ballNum)
                        return
                    }
                } else {
                    val stake = if (pendingBetStake > 0) pendingBetStake else lastBetStake
                    resolveBetResult(cleanPid, won, if (won) (stake * 0.92) else 0.0)
                }
            }
        }
        updateLast20HistoryUi()
    }

    private fun handleGameHistoryBatch(batchJson: String) {
        try {
            val arr = JSONArray(batchJson)
            if (arr.length() == 0) return

            var addedAny = false
            synchronized(historicalItems) {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val p = obj.optString("p").filter { it.isDigit() }
                    val o = obj.optString("o")
                    val n = obj.optInt("n", if (o.equals("BIG", ignoreCase = true)) 7 else 2)

                    if (p.length >= 10 && historicalItems.none { it.periodId == p }) {
                        historicalItems.add(HistoryItem(number = n, outcome = o, periodId = p))
                        addedAny = true
                    }
                }
                historicalItems.sortByDescending { it.periodId.filter { ch -> ch.isDigit() }.toLongOrNull() ?: 0L }
                while (historicalItems.size > 50) historicalItems.removeAt(historicalItems.lastIndex)
            }

            if (addedAny) {
                updateLastRoundUi()
                val top6 = synchronized(historicalItems) { historicalItems.take(6) }
                last6History.clear()
                for (item in top6.reversed()) {
                    last6History.add(if (item.outcome.equals("BIG", ignoreCase = true)) "B" else "S")
                }
                updateLast6Visuals()
                recalculateAiSignal()
                checkAndResolvePendingBet()
                updateLast20HistoryUi()
            }
        } catch (_: Exception) {}
    }

    private fun handleMyHistoryScraped(jsonStr: String) {
        try {
            val arr = JSONArray(jsonStr)
            if (arr.length() == 0) return

            val targetPeriod = pendingBetPeriodId.filter { it.isDigit() }
            var updatedHistory = false

            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val pid = obj.optString("periodId").filter { it.isDigit() }
                val isSettled = obj.optBoolean("isSettled", false)
                val status = obj.optString("status", "").uppercase()
                val profit = obj.optDouble("profit", 0.0)
                val choice = obj.optString("choice")
                val num = obj.optInt("number", -1)

                // 1. Feed settled round outcomes into historicalItems so AI signals keep working on any tab
                if (pid.length >= 10 && isSettled) {
                    val outcome = if (choice.isNotEmpty() && (status.contains("WIN") || status.contains("LOSS"))) {
                        if (status.contains("WIN")) {
                            choice.uppercase()
                        } else {
                            when (choice.uppercase()) {
                                "BIG" -> "SMALL"
                                "SMALL" -> "BIG"
                                "RED" -> "GREEN"
                                "GREEN" -> "RED"
                                else -> "SMALL"
                            }
                        }
                    } else ""
                    if (outcome.isNotEmpty()) {
                        synchronized(historicalItems) {
                            if (historicalItems.none { it.periodId == pid }) {
                                val n = if (num in 0..9) num else {
                                    when (outcome) {
                                        "BIG" -> 7
                                        "RED" -> 8
                                        "GREEN" -> 7
                                        else -> 2
                                    }
                                }
                                historicalItems.add(HistoryItem(number = n, outcome = outcome, periodId = pid))
                                historicalItems.sortByDescending { it.periodId.filter { ch -> ch.isDigit() }.toLongOrNull() ?: 0L }
                                while (historicalItems.size > 50) historicalItems.removeAt(historicalItems.lastIndex)
                                updatedHistory = true
                            }
                        }
                    }
                }

                // 2. Resolve active bot bet only when truly settled
                val targetPid = if (pendingBetPeriodId.isNotEmpty()) pendingBetPeriodId.filter { it.isDigit() } else lastBetPeriodId.filter { it.isDigit() }
                if (targetPid.isNotEmpty() && pid.isNotEmpty()) {
                    val isMatch = (pid == targetPid || (pid.length >= 6 && targetPid.length >= 6 && (pid.endsWith(targetPid) || targetPid.endsWith(pid))))
                    if (isMatch && isSettled && (status.contains("WIN") || status.contains("LOSS") || status.contains("LOSE"))) {
                        val isWin = status.contains("WIN")
                        if (lastResolvedPeriodId == pid) {
                            if (lastResolvedWasWin != isWin) {
                                overturnBetResult(pid, isWin, if (choice.isNotEmpty()) choice else (if (isWin) "WIN" else "LOSS"), num)
                                break
                            }
                        } else {
                            val stake = if (pendingBetStake > 0) pendingBetStake else lastBetStake
                            val profitVal = if (isWin) (if (profit > 0) profit else (stake * 0.92)) else 0.0
                            resolveBetResult(pid, isWin, profitVal)
                            break
                        }
                    }
                }
            }

            if (updatedHistory) {
                updateLast20HistoryUi()
                recalculateAiSignal()
            }
        } catch (_: Exception) {}
    }

    private fun recalculateAiSignal() {
        val pred = VipSignalEngine.compute(
            strategy = selectedStrategy,
            history = synchronized(historicalItems) { ArrayList(historicalItems) },
            periodId = currentLivePeriodId,
            consecutiveLoss = (currentLevel - 1).coerceAtLeast(0)
        )

        currentSignal = pred.prediction
        currentSignalStrength = pred.confidence

        binding.tvSignalDirection.text = currentSignal
        binding.tvSignalStrength.text = "$currentSignalStrength% strength • ${pred.botName}"
        binding.pbSignalStrength.progress = currentSignalStrength

        when (currentSignal.uppercase()) {
            "BIG" -> {
                binding.tvSignalDirection.setTextColor(Color.parseColor("#FFD700"))
                binding.ivSignal3DArrow.setImageResource(R.drawable.icon_signal_big_3d)
            }
            "SMALL" -> {
                binding.tvSignalDirection.setTextColor(Color.parseColor("#00E676"))
                binding.ivSignal3DArrow.setImageResource(R.drawable.icon_signal_small_3d)
            }
            "RED" -> {
                binding.tvSignalDirection.setTextColor(Color.parseColor("#EF4444"))
                binding.ivSignal3DArrow.setImageResource(R.drawable.icon_signal_big_3d)
            }
            "GREEN" -> {
                binding.tvSignalDirection.setTextColor(Color.parseColor("#10B981"))
                binding.ivSignal3DArrow.setImageResource(R.drawable.icon_signal_small_3d)
            }
            else -> {
                binding.tvSignalDirection.setTextColor(Color.parseColor("#FFD700"))
                binding.ivSignal3DArrow.setImageResource(R.drawable.icon_signal_big_3d)
            }
        }
    }

    private fun updateLast6Visuals() {
        val indicators = listOf(
            binding.h61, binding.h62, binding.h63,
            binding.h64, binding.h65, binding.h66
        )
        for (i in indicators.indices) {
            if (i < last6History.size) {
                val tag = last6History[i]
                indicators[i].text = tag
                if (tag.equals("B", ignoreCase = true)) {
                    indicators[i].setBackgroundResource(R.drawable.bg_history_b)
                } else {
                    indicators[i].setBackgroundResource(R.drawable.bg_history_s)
                }
            } else {
                indicators[i].text = "-"
                indicators[i].setBackgroundResource(R.drawable.bg_history_empty)
            }
        }
    }

    private fun updateDashboardStatsUI() {
        binding.tvWinLossRatio.text = "$sessionWins / $sessionLosses"
        val total = sessionWins + sessionLosses
        val winRate = if (total > 0) ((sessionWins.toDouble() / total) * 100).toInt() else 0
        binding.tvWinRate.text = "$winRate% win"

        val profitStr = String.format(Locale.US, "%s₹%.2f", if (sessionProfit >= 0) "+" else "-", Math.abs(sessionProfit))
        binding.tvProfitAmount.text = profitStr
        if (sessionProfit >= 0) {
            binding.tvProfitAmount.setTextColor(ContextCompat.getColor(this, R.color.vip_success))
        } else {
            binding.tvProfitAmount.setTextColor(ContextCompat.getColor(this, R.color.vip_error))
        }

        binding.tvNextBet.text = "₹${String.format(Locale.US, "%.2f", nextBetAmount)}"

        binding.tvTargetHeader.text = "TARGET ₹${String.format(Locale.US, "%.2f", targetAmount)}"
        val pct = if (targetAmount > 0) ((sessionProfit / targetAmount) * 100).toInt().coerceIn(0, 100) else 0
        val remaining = (targetAmount - sessionProfit).coerceAtLeast(0.0)
        binding.tvTargetDetails.text = "$pct% • ₹${String.format(Locale.US, "%.2f", remaining)} to go"
        binding.pbTargetProgress.progress = pct

        updateLast20HistoryUi()
    }

    private data class Last20Badge(
        val text: String,
        val type: BadgeType
    )
    private enum class BadgeType { WIN, LOSS, BIG, SMALL, EMPTY }

    private fun updateLast20HistoryUi() {
        binding.layoutLast20Strip.removeAllViews()

        val totalSlots = 20
        val density = resources.displayMetrics.density
        val badgeSize = (9.5f * density).toInt()
        val margin = (1.5f * density).toInt()

        val badges = mutableListOf<Last20Badge>()

        // 1. Check settled bot bet records
        val settledBets = executedBetRecords.filter { it.status == "WIN" || it.status == "LOSS" }
        if (settledBets.isNotEmpty()) {
            val takeCount = settledBets.take(totalSlots).reversed()
            for (bet in takeCount) {
                if (bet.status.equals("WIN", ignoreCase = true)) {
                    badges.add(Last20Badge("W", BadgeType.WIN))
                } else {
                    badges.add(Last20Badge("L", BadgeType.LOSS))
                }
            }
        } else {
            // 2. If no bot bets placed yet: populate from scraped Game History
            val hist = synchronized(historicalItems) { ArrayList(historicalItems) }
            val recent = hist.take(totalSlots).reversed()
            for (item in recent) {
                val isBig = item.outcome.equals("BIG", ignoreCase = true)
                badges.add(Last20Badge(if (isBig) "B" else "S", if (isBig) BadgeType.BIG else BadgeType.SMALL))
            }
        }

        // Fill remaining slots on the left with "-"
        val emptyCount = (totalSlots - badges.size).coerceAtLeast(0)
        for (i in 0 until emptyCount) {
            val tv = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(badgeSize, badgeSize).apply {
                    if (binding.layoutLast20Strip.childCount > 0) marginStart = margin
                }
                setBackgroundResource(R.drawable.bg_history_empty)
                gravity = Gravity.CENTER
                text = "-"
                setTextColor(ContextCompat.getColor(this@GameWebActivity, R.color.vip_text_hint))
                textSize = 6.5f
                typeface = Typeface.DEFAULT_BOLD
            }
            binding.layoutLast20Strip.addView(tv)
        }

        // Add real badges
        for (badge in badges) {
            val tv = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(badgeSize, badgeSize).apply {
                    if (binding.layoutLast20Strip.childCount > 0) marginStart = margin
                }
                val bgRes = when (badge.type) {
                    BadgeType.WIN -> R.drawable.bg_history_w
                    BadgeType.LOSS -> R.drawable.bg_history_l
                    BadgeType.BIG -> R.drawable.bg_history_b
                    BadgeType.SMALL -> R.drawable.bg_history_s
                    BadgeType.EMPTY -> R.drawable.bg_history_empty
                }
                setBackgroundResource(bgRes)
                gravity = Gravity.CENTER
                text = badge.text
                setTextColor(ContextCompat.getColor(this@GameWebActivity, R.color.vip_white))
                textSize = 6.5f
                typeface = Typeface.DEFAULT_BOLD
            }
            binding.layoutLast20Strip.addView(tv)
        }
    }

    private fun calculateStakeForLevel(level: Int): Double {
        val base = if (baseBetAmount > 0.0) baseBetAmount else calculateAiSuggestedBaseBet()
        val mult = if (is2xMultiplier) 2.0 else 3.0
        val factor = Math.pow(mult, (level - 1).coerceAtLeast(0).toDouble())
        val rawStake = Math.round(base * factor).toDouble().coerceAtLeast(1.0)
        return if (currentBalance > 0.0) rawStake.coerceAtMost(currentBalance.coerceAtLeast(1.0)) else rawStake
    }

    private fun calculateAiSuggestedBaseBet(): Double {
        val bal = if (currentBalance > 0.0) currentBalance else 1000.0
        val n = maxLevels.coerceIn(1, 15)
        val m = if (is2xMultiplier) 2.0 else 3.0
        var sum = 0.0
        var p = 1.0
        for (i in 0 until n) {
            sum += p
            p *= m
        }
        return Math.floor(bal / sum).coerceAtLeast(1.0)
    }

    private fun toggleAutoBet() {
        if (!isAutoBetRunning) {
            if (!canStartTrading()) return
            // If target profit was previously reached, start a fresh session on user manual click!
            if (targetAmount > 0.0 && sessionProfit >= targetAmount) {
                sessionProfit = 0.0
                sessionWins = 0
                sessionLosses = 0
                consecutiveLossStreakAmount = 0.0
                currentLevel = 1
                nextBetAmount = calculateStakeForLevel(1)
                updateDashboardStatsUI()
                showVipNotification("New Session: Target Profit ₹${targetAmount.toInt()}", R.drawable.icon_radar_3d)
            }
            isAutoBetRunning = true
            binding.btnStopAuto.text = "■  STOP AUTO"
            binding.btnStopAuto.setBackgroundResource(R.drawable.bg_bot_btn_stopped)
            binding.tvMiniBotText.text = "PANTHER • AUTO ACTIVE"
            showVipNotification("AUTO TRADING STARTED", R.drawable.icon_radar_3d)

            val cleanPid = currentLivePeriodId.filter { it.isDigit() }
            if (cleanPid.isEmpty() || currentTimeLeftSec < 0) {
                Toast.makeText(this, "Please open Win Go (Lottery) game on the website to begin auto betting", Toast.LENGTH_LONG).show()
            }
        } else {
            isAutoBetRunning = false
            setScreenTouchLock(false)
            binding.btnStopAuto.text = "▶  START AUTO"
            binding.btnStopAuto.setBackgroundResource(R.drawable.bg_bot_btn_running)
            binding.tvMiniBotText.text = "PANTHER • STOPPED"
            showVipNotification("AUTO TRADING PAUSED", R.drawable.icon_radar_3d)
        }
    }

    private fun triggerBetOnce() {
        if (!canStartTrading()) return
        val cleanPeriod = currentLivePeriodId.filter { it.isDigit() }
        if (cleanPeriod.isEmpty()) {
            Toast.makeText(this, "Waiting for current round info...", Toast.LENGTH_SHORT).show()
            return
        }
        executeAutoBet(cleanPeriod, currentSignal, nextBetAmount)
    }

    private fun resetSessionStats() {
        sessionWins = 0
        sessionLosses = 0
        sessionProfit = 0.0
        currentLevel = 1
        consecutiveLossStreakAmount = 0.0
        nextBetAmount = calculateStakeForLevel(1)
        synchronized(executedBetRecords) {
            executedBetRecords.clear()
        }
        pendingBetPeriodId = ""
        pendingBetChoice = ""
        pendingBetStake = 0.0
        lastBetPeriodId = ""
        lastResolvedPeriodId = ""
        lastResolvedWasWin = false
        binding.tvLastRoundWinBanner.text = "READY FOR AUTO BET"
        binding.tvLastRoundWinBanner.setTextColor(ContextCompat.getColor(this, R.color.vip_gold))
        binding.webView.evaluateJavascript("window._tigerLastBetPeriod = '';", null)
        updateDashboardStatsUI()
        Toast.makeText(this, "Session Statistics Reset to 0", Toast.LENGTH_SHORT).show()
    }

    private fun showGameModePicker() {
        val modes = arrayOf("WinGo 30s", "WinGo 1Min", "WinGo 3Min", "WinGo 5Min")
        AlertDialog.Builder(this)
            .setTitle("Select Game Mode")
            .setItems(modes) { _, which ->
                when (which) {
                    0 -> { selectedModeSeconds = 30; selectedModeLabel = "30 SEC" }
                    1 -> { selectedModeSeconds = 60; selectedModeLabel = "1 MIN" }
                    2 -> { selectedModeSeconds = 180; selectedModeLabel = "3 MIN" }
                    3 -> { selectedModeSeconds = 300; selectedModeLabel = "5 MIN" }
                }
                binding.btnGameMode.text = "$selectedModeLabel ▼"
                // No automatic game mode clicking on the site
                Toast.makeText(this, "Mode switched to $selectedModeLabel", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun openBotSettings() {
        val dialog = BotSettingsBottomSheet.newInstance(
            strategy = selectedStrategy,
            targetProfit = targetAmount,
            firstBet = baseBetAmount,
            maxLevels = maxLevels,
            is2x = is2xMultiplier
        )
        dialog.onSaveListener = { strat, targ, base, maxL, is2x ->
            selectedStrategy = strat
            targetAmount = targ
            baseBetAmount = base
            maxLevels = maxL
            is2xMultiplier = is2x

            prefManager.selectedStrategy = strat
            prefManager.targetProfit = targ
            prefManager.firstBetAmount = base
            prefManager.maxLevels = maxL
            prefManager.is2xMultiplier = is2x

            nextBetAmount = calculateStakeForLevel(currentLevel)
            updateDashboardStatsUI()
            recalculateAiSignal()
            Toast.makeText(this, "Settings Applied", Toast.LENGTH_SHORT).show()
        }
        dialog.onOpenCalculatorClicked = {
            binding.root.postDelayed({
                openLevelCalculator()
            }, 180)
        }
        dialog.onOpenProfileClicked = {
            binding.root.postDelayed({
                openUserProfile()
            }, 180)
        }
        dialog.show(supportFragmentManager, "BotSettingsDialog")
    }

    private fun openUserProfile() {
        if (isFinishing || isDestroyed) return
        val profileSheet = UserProfileBottomSheet.newInstance()
        profileSheet.show(supportFragmentManager, UserProfileBottomSheet.TAG)
    }

    private fun openLevelCalculator() {
        val calcSheet = LevelCalculatorBottomSheet.newInstance(
            walletBalance = currentBalance,
            initialBudget = stopLossBudget,
            initialLevels = maxLevels,
            is2x = is2xMultiplier
        )
        calcSheet.onApplyListener = { baseBet, levels, stopLoss, is2x ->
            baseBetAmount = baseBet
            currentLevel = 1
            consecutiveLossStreakAmount = 0.0
            maxLevels = levels
            stopLossBudget = stopLoss
            is2xMultiplier = is2x

            prefManager.firstBetAmount = baseBet
            prefManager.maxLevels = levels
            prefManager.stopLossBudget = stopLoss
            prefManager.is2xMultiplier = is2x

            nextBetAmount = calculateStakeForLevel(1)
            updateDashboardStatsUI()
            Toast.makeText(this, "Calculator Stakes Applied", Toast.LENGTH_SHORT).show()
        }
        calcSheet.show(supportFragmentManager, "LevelCalculatorSheet")
    }

    private fun openBetHistory() {
        val sheet = BetHistoryBottomSheet.newInstance(
            totalBets = executedBetRecords.size,
            wins = sessionWins,
            losses = sessionLosses,
            profit = sessionProfit,
            records = executedBetRecords
        )
        sheet.show(supportFragmentManager, "BetHistorySheet")
    }

    fun showVipNotification(text: String, iconRes: Int) {
        binding.tvToastMessage.text = text
        binding.ivToastIcon.setImageResource(iconRes)
        binding.cardVipToast.visibility = View.VISIBLE
        binding.cardVipToast.alpha = 0f
        binding.cardVipToast.animate().alpha(1f).setDuration(200).start()

        mainHandler.removeCallbacksAndMessages("VIP_TOAST")
        mainHandler.postDelayed({
            binding.cardVipToast.animate().alpha(0f).setDuration(250).withEndAction {
                binding.cardVipToast.visibility = View.GONE
            }.start()
        }, 2600)
    }

    /**
     * Complete DOM JavaScript Engine injected directly into WebView.
     * Ported from TigerAutoRobot for 100% reliable countdown, period, balance, and betting.
     */
    private fun injectAutomationScript() {
        val script = """
            (function() {
                if (window._bdgAutomationInjected) {
                    return;
                }
                window._bdgAutomationInjected = true;

                function enableFlutterSemantics() {}

                function getAllDeepNodes(root) {
                    if (!root) root = document.body;
                    let list = [];
                    function walk(node) {
                        if (!node) return;
                        list.push(node);
                        if (node.shadowRoot) {
                            walk(node.shadowRoot);
                        }
                        let children = node.children || [];
                        for (let i = 0; i < children.length; i++) {
                            walk(children[i]);
                        }
                    }
                    walk(root);
                    return list;
                }

                function getNodeText(node) {
                    if (!node) return '';
                    let aria = (node.getAttribute && node.getAttribute('aria-label')) || '';
                    let inner = (node.innerText || node.textContent || '').trim();
                    if (aria && inner && aria !== inner) return aria + ' ' + inner;
                    return aria || inner || (node.value ? '' + node.value : '');
                }

                // Automatic game/mode selection system completely removed

                const KWG_KEY = 'ERT$%&Dfgdgdg23453DFGQETWET245atdgdgw@';

                function kwgXor(arr, isStr) {
                    let out = isStr ? '' : new Uint8Array(arr.length);
                    for (let i = 0; i < arr.length; i++) {
                        let c = (typeof arr === 'string') ? arr.charCodeAt(i) : arr[i];
                        let x = c ^ KWG_KEY.charCodeAt(i % KWG_KEY.length);
                        if (isStr) out += String.fromCharCode(x);
                        else out[i] = x;
                    }
                    return out;
                }

                function decodeVarint(arr, offset) {
                    let val = 0n, shift = 0n, pos = offset;
                    while (pos < arr.length) {
                        let b = arr[pos++];
                        val |= BigInt(b & 0x7f) << shift;
                        shift += 7n;
                        if (!(b & 0x80)) break;
                    }
                    return { val: val, pos: pos };
                }

                function decodeCurrentIssueProto(bytes) {
                    try {
                        let pos = 2;
                        let t = decodeVarint(bytes, pos);
                        let l = decodeVarint(bytes, t.pos);
                        let sub = bytes.slice(l.pos, l.pos + Number(l.val));
                        let sPos = 0;
                        let period = '';
                        let timeRem = -1;
                        while (sPos < sub.length) {
                            let stag = decodeVarint(sub, sPos);
                            let sfn = Number(stag.val >> 3n);
                            let sval = decodeVarint(sub, stag.pos);
                            sPos = sval.pos;
                            if (sfn === 2) period = sval.val.toString();
                            if (sfn === 3) timeRem = Number(sval.val);
                        }
                        return { period: period, timeRem: timeRem };
                    } catch(e) { return null; }
                }

                function decodeHistoryListProto(bytes) {
                    try {
                        let pos = 0;
                        let sub = null;
                        while (pos < bytes.length) {
                            let tag = decodeVarint(bytes, pos);
                            let wire = Number(tag.val & 7n);
                            let fn = Number(tag.val >> 3n);
                            pos = tag.pos;
                            if (wire === 0) {
                                let v = decodeVarint(bytes, pos);
                                pos = v.pos;
                            } else if (wire === 2) {
                                let l = decodeVarint(bytes, pos);
                                pos = l.pos;
                                let len = Number(l.val);
                                if (fn === 3) {
                                    sub = bytes.slice(pos, pos + len);
                                    break;
                                } else {
                                    pos += len;
                                }
                            } else break;
                        }
                        if (!sub) return [];
                        pos = 0;
                        let list = [];
                        while (pos < sub.length) {
                            let tag = decodeVarint(sub, pos);
                            let wire = Number(tag.val & 7n);
                            let fn = Number(tag.val >> 3n);
                            pos = tag.pos;
                            if (wire === 2 && fn === 2) {
                                let l = decodeVarint(sub, pos);
                                pos = l.pos;
                                let rowBytes = sub.slice(pos, pos + Number(l.val));
                                pos += Number(l.val);
                                let rPos = 0;
                                let pid = '';
                                let num = -1;
                                while (rPos < rowBytes.length) {
                                    let rTag = decodeVarint(rowBytes, rPos);
                                    let rWire = Number(rTag.val & 7n);
                                    let rFn = Number(rTag.val >> 3n);
                                    rPos = rTag.pos;
                                    if (rWire === 0 && rFn === 1) {
                                        let rv = decodeVarint(rowBytes, rPos);
                                        rPos = rv.pos;
                                        pid = rv.val.toString();
                                    } else if (rWire === 2 && rFn === 2) {
                                        let rl = decodeVarint(rowBytes, rPos);
                                        rPos = rl.pos;
                                        let sLen = Number(rl.val);
                                        let s = '';
                                        for (let si = 0; si < sLen; si++) s += String.fromCharCode(rowBytes[rPos++]);
                                        num = parseInt(s, 10);
                                    } else if (rWire === 0) {
                                        let rv = decodeVarint(rowBytes, rPos);
                                        rPos = rv.pos;
                                    } else if (rWire === 2) {
                                        let rl = decodeVarint(rowBytes, rPos);
                                        rPos = rl.pos + Number(rl.val);
                                    }
                                }
                                if (pid && num >= 0) {
                                    let choice = num >= 5 ? 'BIG' : 'SMALL';
                                    list.push({ p: pid, o: choice, n: num });
                                }
                            } else if (wire === 0) {
                                let v = decodeVarint(sub, pos);
                                pos = v.pos;
                            } else if (wire === 2) {
                                let l = decodeVarint(sub, pos);
                                pos = l.pos + Number(l.val);
                            } else break;
                        }
                        return list;
                    } catch(e) { return []; }
                }

                // Passive XHR Hook: Intercepts all traffic between Flutter Web and KWG Game Server
                try {
                    let origOpen = XMLHttpRequest.prototype.open;
                    let origSend = XMLHttpRequest.prototype.send;
                    XMLHttpRequest.prototype.open = function(method, url) {
                        this._reqUrl = url;
                        return origOpen.apply(this, arguments);
                    };
                    XMLHttpRequest.prototype.send = function(body) {
                        let url = this._reqUrl || '';
                        this.addEventListener('load', function() {
                            if (this.status === 200 && this.response) {
                                try {
                                    let bytes = new Uint8Array(this.response);
                                    if (url.includes('uuserwallet/getbalance')) {
                                        let dec = kwgXor(bytes, true);
                                        let mBal = dec.match(/([0-9,]+\.[0-9]{2})/);
                                        if (mBal && window.VipRobotBridge && window.VipRobotBridge.onBalanceScraped) {
                                            window.VipRobotBridge.onBalanceScraped(mBal[1].replace(/,/g, ''));
                                        }
                                    } else if (url.includes('lissue/getcurrentissue')) {
                                        let decBytes = kwgXor(bytes, false);
                                        let res = decodeCurrentIssueProto(decBytes);
                                        if (res && res.period && window.VipRobotBridge && window.VipRobotBridge.onPeriodDetected) {
                                            window.VipRobotBridge.onPeriodDetected(res.period, res.timeRem, "WinGo");
                                        }
                                    } else if (url.includes('lcode/getpageresult')) {
                                        let decBytes = kwgXor(bytes, false);
                                        let list = decodeHistoryListProto(decBytes);
                                        if (list && list.length > 0 && window.VipRobotBridge && window.VipRobotBridge.onGameHistoryBatch) {
                                            window.VipRobotBridge.onGameHistoryBatch(JSON.stringify(list));
                                        }
                                    }
                                } catch(_) {}
                            }
                        });
                        return origSend.apply(this, arguments);
                    };
                } catch(e) {}

                function pollGameData() {
                    let token = '';
                    try {
                        let rawTok = localStorage.getItem('flutter.token');
                        if (typeof rawTok === 'string') rawTok = JSON.parse(rawTok);
                        token = String(rawTok);
                    } catch(_) {}
                    if (!token || token.length < 5 || token === 'null') return;

                    let lotteryType = 10005;
                    let m = ('$selectedModeLabel').toLowerCase();
                    if (m.indexOf('1') !== -1) lotteryType = 10001;
                    else if (m.indexOf('3') !== -1) lotteryType = 10003;
                    else if (m.indexOf('5') !== -1) lotteryType = 10004;

                    // 1. Balance
                    let xBal = new XMLHttpRequest();
                    xBal.open('POST', 'https://kwg.kwggame.club/uuserwallet/getbalance', true);
                    xBal.responseType = 'arraybuffer';
                    xBal.setRequestHeader('Content-Type', 'application/octet-stream');
                    xBal.setRequestHeader('identity', token);
                    xBal.setRequestHeader('lang', '2');
                    xBal.send(null);

                    // 2. Current Issue
                    let xIss = new XMLHttpRequest();
                    xIss.open('POST', 'https://kwg.kwggame.club/lissue/getcurrentissue', true);
                    xIss.responseType = 'arraybuffer';
                    xIss.setRequestHeader('Content-Type', 'application/octet-stream');
                    xIss.setRequestHeader('identity', token);
                    xIss.setRequestHeader('lang', '2');
                    let issBody = '\n\u0015' + JSON.stringify({ LotteryType: lotteryType });
                    xIss.send(kwgXor(issBody, false));

                    // 3. Page Result (History)
                    let xHis = new XMLHttpRequest();
                    xHis.open('POST', 'https://kwg.kwggame.club/lcode/getpageresult', true);
                    xHis.responseType = 'arraybuffer';
                    xHis.setRequestHeader('Content-Type', 'application/octet-stream');
                    xHis.setRequestHeader('identity', token);
                    xHis.setRequestHeader('lang', '2');
                    let hisBody = '\nA' + JSON.stringify({ LotteryType: lotteryType, PageIndex: 1, PageSize: 10, TimeType: null });
                    xHis.send(kwgXor(hisBody, false));
                }
                window.pollGameData = pollGameData;
                window.scrapeBalance = pollGameData;
                setTimeout(pollGameData, 300);

                function extractTimeRemainingAndPeriod() {
                    let timeLeftSec = -1;
                    let periodId = '';

                    // 1. Live values from CanvasKit hooks if available
                    if (window._liveKwgPeriod) {
                        periodId = window._liveKwgPeriod;
                    }
                    if (typeof window._liveKwgCountdown === 'number' && window._liveKwgCountdown >= 0) {
                        timeLeftSec = window._liveKwgCountdown;
                    }

                    if (timeLeftSec !== -1 && periodId) {
                        return { timeLeftSec: timeLeftSec, periodId: periodId };
                    }

                    try {
                        enableFlutterSemantics();
                        let allNodes = getAllDeepNodes(document.body);
                        for (let node of allNodes) {
                            let txt = getNodeText(node);
                            if (!txt || txt.length > 100) continue;

                            if (timeLeftSec === -1) {
                                let m = txt.match(/(\d)\s*(\d)\s*:\s*(\d)\s*(\d)/) || txt.match(/(\d{1,2})\s*:\s*(\d{2})/);
                                if (m) {
                                    let v = (m.length >= 5) ? ((parseInt(m[1] + m[2]) * 60) + parseInt(m[3] + m[4])) : ((parseInt(m[1]) * 60) + parseInt(m[2]));
                                    if (v <= $selectedModeSeconds) timeLeftSec = v;
                                }
                            }

                            if (!periodId) {
                                let pm = txt.match(/\b(20\d{12,18}|\d{14,20})\b/);
                                if (pm) {
                                    periodId = pm[1];
                                }
                            }

                            if (timeLeftSec !== -1 && periodId) break;
                        }
                    } catch(e) {}



                    return { timeLeftSec: timeLeftSec, periodId: periodId };
                }

                function scrapeGameResults(currentPeriod, timeLeftSec) {
                    try {
                        let targetPeriod = (window._tigerLastBetPeriod || '').trim();
                        if (!targetPeriod) return;

                        if (currentPeriod && timeLeftSec > 0) {
                            let cp1 = currentPeriod.replace(/\D/g, '');
                            let tp1 = targetPeriod.replace(/\D/g, '');
                            if (cp1 === tp1 || (cp1.length >= 4 && tp1.length >= 4 && (cp1.endsWith(tp1) || tp1.endsWith(cp1)))) {
                                return;
                            }
                        }

                        // Priority 1: Check visible Game Popups (Win or Lose modal)
                        let allPopups = document.querySelectorAll('.van-popup, [class*="dialog"], [class*="modal"], [class*="popup"]');
                        for (let p of allPopups) {
                            if (p.offsetParent === null || p.style.display === 'none') continue;
                            let txt = (p.innerText || p.textContent || '').trim();
                            if (txt.length > 600) continue;

                            let isWinModal = /Congratulations/i.test(txt);
                            let isLoseModal = /\b(?:Lose|Failed)\b/i.test(txt) && !isWinModal;

                            if (isWinModal || isLoseModal) {
                                let pm = txt.match(/\b(20\d{12,18}|\d{15,20})\b/);
                                if (pm) {
                                    let pId = pm[1];
                                    let p1 = pId.replace(/\D/g, '');
                                    let p2 = targetPeriod.replace(/\D/g, '');
                                    if (p1 === p2 || (p1.length >= 4 && p2.length >= 4 && (p1.endsWith(p2) || p2.endsWith(p1)))) {
                                        if (isWinModal) {
                                            let bonusM = txt.match(/(?:Bonus|win|amount|\+)\s*[₹Rs:]?\s*([0-9]+(?:\.[0-9]+)?)/i);
                                            let profit = bonusM ? parseFloat(bonusM[1]) : 0.0;
                                            if (window.VipRobotBridge) {
                                                window.VipRobotBridge.onBetResultResolved(pId, true, profit);
                                                window._tigerLastBetPeriod = '';
                                            }
                                        } else {
                                            if (window.VipRobotBridge) {
                                                window.VipRobotBridge.onBetResultResolved(pId, false, 0.0);
                                                window._tigerLastBetPeriod = '';
                                            }
                                        }
                                        try {
                                            let closeBtn = p.querySelector('.van-icon-cross, [class*="close"], [class*="cancel"], button');
                                            if (closeBtn) closeBtn.click();
                                            else {
                                                let overlay = document.querySelector('.van-overlay');
                                                if (overlay) overlay.click();
                                            }
                                        } catch(e) {}
                                        return;
                                    }
                                }
                            }
                        }
                    } catch(e){}
                }

                function scrapeGameHistoryTable(currentPeriod) {
                    try {
                        let candidateContainers = document.querySelectorAll(
                            '.van-tabs__content .van-tab__pane, .van-tab__pane, [role="tabpanel"], [class*="game-history"], [class*="history"], [class*="record"], table, .van-list, div'
                        );
                        let historyPane = null;
                        for (let p of candidateContainers) {
                            if (p.style.display === 'none') continue;
                            let pTxt = (p.textContent || p.innerText || '').trim();
                            if (/Period/i.test(pTxt) && (/Big Small/i.test(pTxt) || /Big\s*\/\s*Small/i.test(pTxt) || /Color/i.test(pTxt) || /Number/i.test(pTxt))) {
                                historyPane = p;
                                break;
                            }
                        }
                        if (!historyPane) historyPane = document.body;

                        let candidateRows = historyPane.querySelectorAll('.van-row, tr, [class*="row"], [class*="record"], [class*="item"], div[role="row"], .van-cell');
                        if (!candidateRows || candidateRows.length === 0) return;

                        let historyBatch = [];
                        let seenPeriods = new Set();
                        let targetPeriod = (window._tigerLastBetPeriod || '').trim();

                        for (let r of candidateRows) {
                            let txt = (r.innerText || r.textContent || '').trim();
                            if (txt.length < 3 || txt.length > 200) continue;
                            if (/Detail|Time\s*remaining|Total\s*amount|Order\s*number|Delivery|How\s*to\s*play|Purchase\s*amount/i.test(txt)) continue;
                            if (/\d{4}-\d{2}-\d{2}|\d{2}:\d{2}:\d{2}/.test(txt)) continue;

                            let pMatches = txt.match(/\b(20\d{12,18}|\d{15,20})\b/g);
                            if (!pMatches || pMatches.length !== 1) continue;

                            let pId = pMatches[0];
                            if (seenPeriods.has(pId)) continue;

                            let cleanTxt = txt.replace(pId, ' ');
                            let choiceMatch = cleanTxt.match(/\b(Big|Small)\b/i);
                            let resChoice = choiceMatch ? (choiceMatch[1].charAt(0).toUpperCase() + choiceMatch[1].slice(1).toLowerCase()) : "";

                            let ballNum = -1;
                            let directCells = r.querySelectorAll('.van-col, td, th, [class*="col"], [class*="cell"], [class*="ball"], [class*="circle"], span, div');
                            for (let c of directCells) {
                                let cTxt = (c.innerText || c.textContent || '').trim();
                                if (/^[0-9]$/.test(cTxt)) {
                                    ballNum = parseInt(cTxt);
                                    break;
                                }
                            }
                            if (ballNum === -1) {
                                let numTxt = cleanTxt.replace(/\b(Big|Small)\b/gi, ' ');
                                let numMatch = numTxt.match(/\b([0-9])\b/);
                                if (numMatch) ballNum = parseInt(numMatch[1]);
                            }

                            if (ballNum >= 0 && ballNum <= 9) {
                                resChoice = (ballNum >= 5) ? "Big" : "Small";
                            } else if (resChoice) {
                                ballNum = (resChoice === "Big") ? 7 : 3;
                            } else continue;

                            let resColor = (ballNum === 0) ? "Red Violet" : (ballNum === 5) ? "Green Violet" : (ballNum % 2 === 0) ? "Red" : "Green";

                            seenPeriods.add(pId);
                            historyBatch.push({ p: pId, o: resChoice, n: ballNum });

                            if (targetPeriod) {
                                let p1 = pId.replace(/\D/g, '');
                                let p2 = targetPeriod.replace(/\D/g, '');
                                if (p1 === p2 || (p1.length >= 4 && p2.length >= 4 && (p1.endsWith(p2) || p2.endsWith(p1)))) {
                                    if (window.VipRobotBridge) {
                                        window.VipRobotBridge.onGameResultDetectedWithNumber(pId, resChoice, resColor, ballNum);
                                    }
                                }
                            }
                        }

                        if (historyBatch.length > 0 && window.VipRobotBridge) {
                            window.VipRobotBridge.onGameHistoryBatch(JSON.stringify(historyBatch.slice(0, 30)));
                        }
                    } catch(e) {}
                }

                function scrapeMyHistoryList() {
                    try {
                        let allItems = [];
                        let seenPeriods = new Set();
                        let candidateCards = document.querySelectorAll(
                            '.van-collapse-item, [class*="record-item"], [class*="order-item"], [class*="history-item"], [class*="card"], [class*="record"]'
                        );
                        let cardList = [];
                        if (candidateCards && candidateCards.length > 0) {
                            cardList = Array.from(candidateCards);
                        }

                        let walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, null, false);
                        let node;
                        while (node = walker.nextNode()) {
                            let val = (node.nodeValue || '').trim();
                            if (/\b(20\d{12,18}|\d{15,20})\b/.test(val)) {
                                let curr = node.parentElement;
                                let bestCard = null;
                                for (let d = 0; d < 5 && curr && curr !== document.body; d++) {
                                    let cTxt = (curr.innerText || curr.textContent || '').trim();
                                    let pMatches = cTxt.match(/\b(20\d{12,18}|\d{15,20})\b/g);
                                    if (pMatches && pMatches.length > 1) {
                                        let uniq = new Set(pMatches.map(p => p.replace(/\D/g, '')));
                                        if (uniq.size > 1) break;
                                    }
                                    bestCard = curr;
                                    curr = curr.parentElement;
                                }
                                if (bestCard && !cardList.includes(bestCard)) cardList.push(bestCard);
                            }
                        }

                        let targetPeriod = (window._tigerLastBetPeriod || '').trim();
                        let targetDigits = targetPeriod.replace(/\D/g, '');

                        for (let el of cardList) {
                            if (el.closest('table, [class*="game-history"]') ||
                                /Period.*Number.*Big|Color.*Number|Big\s*Small/i.test(el.innerText || '')) {
                                continue;
                            }

                            let rawTxt = (el.innerText || el.textContent || '').trim();
                            if (rawTxt.length < 15 || rawTxt.length > 800) continue;
                            let isOrderCard = /Detail|Unpaid|Waiting|Pending|Select\s*:/i.test(rawTxt) ||
                                              /[+-]\s*[₹Rs]?\s*[0-9]+(?:\.[0-9]+)?/i.test(rawTxt) ||
                                              /Order\s*number|Purchase\s*amount|Delivery/i.test(rawTxt);
                            if (!isOrderCard) continue;

                            let pMatches = rawTxt.match(/\b(20\d{12,18}|\d{15,20})\b/g);
                            if (!pMatches) continue;
                            let uniqueP = Array.from(new Set(pMatches.map(p => p.replace(/\D/g, ''))));
                            if (uniqueP.length !== 1) continue;

                            let pId = uniqueP[0];
                            if (seenPeriods.has(pId)) continue;

                            let cleanTxt = rawTxt
                                .replace(/\d{4}-\d{2}-\d{2}|\d{2}:\d{2}:\d{2}/g, ' ')
                                .replace(/Win\s*Go[^\n\r]*/gi, 'WG_TITLE')
                                .replace(/BDG\s*Win/gi, 'BDG_NAME')
                                .replace(/Win\s*\/\s*lose/gi, 'WL_HDR');

                            let exactWinBadge = false;
                            let exactLoseBadge = false;
                            let hasDetailBtn = false;
                            let hasPendingBadge = false;

                            let badgeElements = el.querySelectorAll('button, span, div, a, p, em, strong');
                            for (let b of badgeElements) {
                                let t = (b.innerText || b.textContent || '').trim();
                                if (/^(?:Win|Succeed)$/i.test(t)) exactWinBadge = true;
                                if (/^(?:Lose|Failed)$/i.test(t)) exactLoseBadge = true;
                                if (/^Detail/i.test(t)) hasDetailBtn = true;
                                if (/^(?:Unpaid|Pending|Wait|Waiting|Settling)$/i.test(t)) hasPendingBadge = true;
                            }

                            let plusM = cleanTxt.match(/\+\s*[₹Rs]?\s*([0-9,]+(?:\.[0-9]+)?)/i);
                            let minusM = cleanTxt.match(/-\s*[₹Rs]?\s*([0-9,]+(?:\.[0-9]+)?)/i);

                            let isUnpaid = hasPendingBadge ||
                                           /\bResult\s*:\s*--/i.test(rawTxt) ||
                                           /\bWinning\s*number\s*:\s*--/i.test(rawTxt) ||
                                           /\b(?:WL_HDR|Result|Winning\s*number)\s*:\s*--/i.test(cleanTxt) ||
                                           (!plusM && !minusM && !exactWinBadge && !exactLoseBadge);

                            let isSettled = false;
                            let status = "UNPAID";
                            let profit = 0.0;

                            if (!isUnpaid) {
                                if (plusM !== null || (exactWinBadge && !exactLoseBadge && !hasDetailBtn)) {
                                    status = "WIN";
                                    profit = plusM ? parseFloat(plusM[1].replace(/,/g, '')) : 0.0;
                                    isSettled = true;
                                } else if (exactLoseBadge || (minusM !== null && !hasPendingBadge)) {
                                    status = "LOSS";
                                    profit = minusM ? -parseFloat(minusM[1].replace(/,/g, '')) : 0.0;
                                    isSettled = true;
                                }
                            }

                            seenPeriods.add(pId);

                            let cardChoice = "";
                            let allCardElems = el.querySelectorAll('div, span, button');
                            for (let elem of allCardElems) {
                                let et = (elem.innerText || elem.textContent || '').trim();
                                if (/^Big$/i.test(et)) { cardChoice = "Big"; break; }
                                if (/^Small$/i.test(et)) { cardChoice = "Small"; break; }
                            }

                            let winNumMatch = cleanTxt.match(/Winning\s*number\s*:\s*([0-9])/i) || cleanTxt.match(/Result\s*:\s*([0-9])/i);
                            let cardNum = winNumMatch ? parseInt(winNumMatch[1]) : -1;

                            allItems.push({
                                periodId: pId,
                                choice: cardChoice,
                                status: status,
                                profit: profit,
                                isSettled: isSettled,
                                number: cardNum
                            });

                            if (targetDigits && (pId === targetDigits || (pId.length >= 6 && targetDigits.length >= 6 && (pId.endsWith(targetDigits) || targetDigits.endsWith(pId))))) {
                                if (isSettled && (status === "WIN" || status === "LOSS")) {
                                    if (window.VipRobotBridge) {
                                        window.VipRobotBridge.onBetResultResolved(pId, status === "WIN", status === "WIN" ? profit : Math.abs(profit));
                                        window._tigerLastBetPeriod = '';
                                    }
                                }
                            }
                        }

                        if (allItems.length > 0 && window.VipRobotBridge && window.VipRobotBridge.onMyHistoryScraped) {
                            window.VipRobotBridge.onMyHistoryScraped(JSON.stringify(allItems.slice(0, 20)));
                        }
                    } catch(e){}
                }

                function scrapeGameInfo() {
                    try {
                        let info = extractTimeRemainingAndPeriod();
                        let periodId = info.periodId;

                        // Run table & history parsing
                        scrapeGameResults(periodId, -1);
                        scrapeGameHistoryTable(periodId);
                        scrapeMyHistoryList();
                    } catch(e){}
                }

                // --- KWG FLUTTER WEB DIRECT AUTO-BET ENGINE ---
                function encodeProtoBet(lotteryType, issue, choice, qty) {
                    let isBig = (choice || '').toUpperCase() === 'BIG';
                    let betPayload = {
                        LotteryType: lotteryType,
                        Issue: String(issue),
                        LstOrder: [
                            {
                                PlayType: 102,
                                BetPlan: "",
                                BetCode: isBig ? "1" : "2",
                                MoneyUnit: 1,
                                BetMultiple: parseInt(qty) || 1
                            }
                        ]
                    };
                    let jsonStr = JSON.stringify(betPayload);
                    let bytes = [0x0A]; // field 1, wire 2
                    let len = jsonStr.length;
                    while (len > 0x7F) {
                        bytes.push((len & 0x7F) | 0x80);
                        len >>>= 7;
                    }
                    bytes.push(len & 0x7F);
                    for (let i = 0; i < jsonStr.length; i++) {
                        bytes.push(jsonStr.charCodeAt(i));
                    }
                    let key = 'ERT$%&Dfgdgdg23453DFGQETWET245atdgdgw@';
                    let out = new Uint8Array(bytes.length);
                    for (let i = 0; i < bytes.length; i++) {
                        out[i] = bytes[i] ^ key.charCodeAt(i % key.length);
                    }
                    return out;
                }

                window.tigerCheckAndDismissResultPopup = function() {
                    try {
                        let canvases = [];
                        function scan(root) {
                            if (!root) return;
                            root.querySelectorAll('canvas').forEach(c => canvases.push(c));
                            root.querySelectorAll('*').forEach(el => {
                                if (el.shadowRoot) scan(el.shadowRoot);
                            });
                        }
                        scan(document);
                        let c = canvases[0];
                        if (!c) return false;

                        let w = c.width;
                        let h = c.height;

                        let testCanvas = document.createElement('canvas');
                        testCanvas.width = 1;
                        testCanvas.height = 1;
                        let ctx = testCanvas.getContext('2d', { willReadFrequently: true });

                        function sampleRGB(xPct, yPct) {
                            ctx.drawImage(c, Math.floor(w * xPct), Math.floor(h * yPct), 1, 1, 0, 0, 1, 1);
                            return ctx.getImageData(0, 0, 1, 1).data;
                        }

                        // Robust Win/Loss Modal Detection (handles BOTH Win gold modal and Lose light blue modal):
                        // 1. Center receipt/scroll check at X=0.50, Y between 0.58 and 0.66:
                        // Win receipt: cream white (255, 239, 188); Lose receipt: pure white (248, 249, 254)
                        let isCardPresent = false;
                        for (let y = 0.58; y <= 0.66; y += 0.02) {
                            let p = sampleRGB(0.50, y);
                            if (p[0] >= 230 && p[1] >= 210 && p[2] >= 155) {
                                isCardPresent = true;
                                break;
                            }
                        }

                        // 2. Dialog backdrop check below receipt at X=0.50, Y between 0.70 and 0.76:
                        // Win backdrop: golden (205, 162, 80); Lose backdrop: light purple/blue (182, 184, 222)
                        let isBackdropPresent = false;
                        for (let y = 0.70; y <= 0.76; y += 0.02) {
                            let p = sampleRGB(0.50, y);
                            let isWinGold = (p[0] >= 170 && p[1] >= 130 && p[2] <= 115 && p[0] > p[2] + 40);
                            let isLoseBlue = (p[0] >= 155 && p[1] >= 155 && p[2] >= 195);
                            if (isWinGold || isLoseBlue) {
                                isBackdropPresent = true;
                                break;
                            }
                        }

                        if (isCardPresent && isBackdropPresent) {
                            console.log('[tigerPopup] Genuine Win/Lose Result popup CONFIRMED! Dismissing modal via close button at (0.50, 0.825)...');
                            window._tigerPopupOpenTime = 0;
                            if (window.VipRobotBridge && window.VipRobotBridge.dispatchNativeClick) {
                                // Tap the round (X) close button right below the popup dialog at (0.50, 0.825)
                                window.VipRobotBridge.dispatchNativeClick(0.50, 0.825);
                            }
                            return true;
                        }
                        return false;
                    } catch(e) {
                        return false;
                    }
                };

                window.tigerExecuteBet = function(choice, qty, isRetry) {
                    try {
                        console.log('[tigerExecuteBet] Invoked for choice=' + choice + ', qty=' + qty + ', isRetry=' + !!isRetry);

                        // 0. Dismiss soft keyboard
                        if (window.VipRobotBridge && window.VipRobotBridge.dismissKeyboard) {
                            window.VipRobotBridge.dismissKeyboard();
                        }

                        // 0b. Dismiss Win/Lose modal if present
                        let hadPopup = false;
                        if (typeof window.tigerCheckAndDismissResultPopup === 'function') {
                            hadPopup = window.tigerCheckAndDismissResultPopup();
                        }

                        function getCanvasAndContext() {
                            let canvases = [];
                            function scan(root) {
                                if (!root) return;
                                root.querySelectorAll('canvas').forEach(c => canvases.push(c));
                                root.querySelectorAll('*').forEach(el => { if (el.shadowRoot) scan(el.shadowRoot); });
                            }
                            scan(document);
                            let c = canvases[0];
                            if (!c) return null;
                            let tc = document.createElement('canvas');
                            tc.width = 4;
                            tc.height = 4;
                            let ctx = tc.getContext('2d', { willReadFrequently: true });
                            return { c: c, tc: tc, ctx: ctx, w: c.width, h: c.height };
                        }

                        function isBottomSheetOpen() {
                            try {
                                let info = getCanvasAndContext();
                                if (!info) return false;
                                let c = info.c, w = info.w, h = info.h;

                                // Total Amount button at bottom right (0.667, 0.88..0.99) - bright yellow/gold: R>=190, G>=150, B<=160
                                let startY = Math.floor(h * 0.88);
                                let scanH = Math.max(8, Math.floor(h * 0.11));
                                let tc = document.createElement('canvas');
                                tc.width = 1;
                                tc.height = scanH;
                                let tctx = tc.getContext('2d', { willReadFrequently: true });
                                if (!tctx) return false;
                                tctx.drawImage(c, Math.floor(w * 0.667), startY, 1, scanH, 0, 0, 1, scanH);
                                let data = tctx.getImageData(0, 0, 1, scanH).data;
                                for (let i = 0; i < data.length; i += 4) {
                                    let r = data[i], g = data[i+1], b = data[i+2];
                                    if (r >= 190 && g >= 150 && b <= 160 && r > b + 30) {
                                        return true;
                                    }
                                }
                                return false;
                            } catch(e) {
                                return false;
                            }
                        }

                        function findTotalAmountButtonY() {
                            try {
                                let info = getCanvasAndContext();
                                if (!info) return 0.965;
                                let c = info.c, w = info.w, h = info.h;
                                let startY = Math.floor(h * 0.88);
                                let scanH = Math.max(8, Math.floor(h * 0.11));
                                let tc = document.createElement('canvas');
                                tc.width = 1;
                                tc.height = scanH;
                                let tctx = tc.getContext('2d', { willReadFrequently: true });
                                if (!tctx) return 0.965;
                                tctx.drawImage(c, Math.floor(w * 0.667), startY, 1, scanH, 0, 0, 1, scanH);
                                let data = tctx.getImageData(0, 0, 1, scanH).data;
                                let goldYs = [];
                                for (let row = 0; row < scanH; row++) {
                                    let idx = row * 4;
                                    let r = data[idx], g = data[idx + 1], b = data[idx + 2];
                                    if (r >= 190 && g >= 150 && b <= 160 && r > b + 30) {
                                        goldYs.push((startY + row) / h);
                                    }
                                }
                                if (goldYs.length > 0) {
                                    let midY = (goldYs[0] + goldYs[goldYs.length - 1]) / 2.0;
                                    return parseFloat(midY.toFixed(3));
                                }
                            } catch(_) {}
                            return 0.965;
                        }

                        function findQuantityBoxY() {
                            try {
                                let info = getCanvasAndContext();
                                if (!info) return 0.820;
                                let c = info.c, w = info.w, h = info.h;
                                let startY = Math.floor(h * 0.78);
                                let scanH = Math.floor(h * 0.08); // Y from 0.78 to 0.86
                                let tc = document.createElement('canvas');
                                tc.width = 1;
                                tc.height = scanH;
                                let tctx = tc.getContext('2d', { willReadFrequently: true });
                                if (!tctx) return 0.820;
                                tctx.drawImage(c, Math.floor(w * 0.67), startY, 1, scanH, 0, 0, 1, scanH);
                                let data = tctx.getImageData(0, 0, 1, scanH).data;
                                let pillYs = [];
                                for (let row = 0; row < scanH; row++) {
                                    let idx = row * 4;
                                    let r = data[idx], g = data[idx + 1], b = data[idx + 2];
                                    // Lavender quantity pill container: R in 115..170, G in 110..160, B in 165..220, or white text
                                    if ((r >= 115 && g >= 110 && b >= 165) || (r >= 220 && g >= 220 && b >= 220)) {
                                        pillYs.push((startY + row) / h);
                                    }
                                }
                                if (pillYs.length > 0) {
                                    let midY = (pillYs[0] + pillYs[pillYs.length - 1]) / 2.0;
                                    return parseFloat(midY.toFixed(3));
                                }
                            } catch(_) {}
                            return 0.820;
                        }

                        function isOperationSuccessVisible() {
                            try {
                                let info = getCanvasAndContext();
                                if (!info) return false;
                                let c = info.c, ctx = info.ctx, w = info.w, h = info.h;

                                // Purple toast box in middle: R in 140..195, G in 55..115, B in 185..245
                                ctx.drawImage(c, Math.floor(w * 0.40), Math.floor(h * 0.54), 2, 2, 0, 0, 2, 2);
                                let pA = ctx.getImageData(0, 0, 1, 1).data;
                                ctx.drawImage(c, Math.floor(w * 0.60), Math.floor(h * 0.54), 2, 2, 0, 0, 2, 2);
                                let pB = ctx.getImageData(0, 0, 1, 1).data;

                                let matchA = (pA[0] >= 140 && pA[0] <= 195 && pA[1] >= 55 && pA[1] <= 115 && pA[2] >= 185 && pA[2] <= 245);
                                let matchB = (pB[0] >= 140 && pB[0] <= 195 && pB[1] >= 55 && pB[1] <= 115 && pB[2] >= 185 && pB[2] <= 245);
                                return matchA || matchB;
                            } catch(e) {
                                return false;
                            }
                        }

                        function dispatchTap(xPct, yPct) {
                            if (window.VipRobotBridge && window.VipRobotBridge.dispatchNativeClick) {
                                window.VipRobotBridge.dispatchNativeClick(parseFloat(xPct), parseFloat(yPct));
                            }
                        }

                        function placeBetFlow() {
                            let upperChoice = (choice || '').toUpperCase();
                            let isRed = upperChoice === 'RED';
                            let isGreen = upperChoice === 'GREEN';
                            let isColorBet = isRed || isGreen;
                            let isBig = upperChoice === 'BIG';
                            let targetQty = parseInt(qty) || 1;
                            let canvasInfo = getCanvasAndContext();
                            let c = canvasInfo ? canvasInfo.c : null;
                            let w = (canvasInfo && canvasInfo.w) || window.innerWidth || 720;
                            let h = (canvasInfo && canvasInfo.h) || window.innerHeight || 1600;
                            let isTablet = (h / w) < 1.6;

                            let totalAmountBtnY = findTotalAmountButtonY();
                            let cancelBtnY = totalAmountBtnY;
                            let qtyBoxY = findQuantityBoxY();
                            let multChipY = parseFloat((totalAmountBtnY - 0.080).toFixed(3));
                            let baseChipY = parseFloat((totalAmountBtnY - 0.215).toFixed(3));

                            // 1. If any stale sheet is open, dismiss it first via Cancel
                            if (isBottomSheetOpen()) {
                                console.log('[tigerExecuteBet] Stale sheet was open, dismissing via Cancel before placing bet...');
                                dispatchTap(0.167, cancelBtnY);
                                setTimeout(proceedClickBetButton, 200);
                                return;
                            }
                            function findButtonCoordinates(isBig) {
                                try {
                                    let info = getCanvasAndContext();
                                    if (!info) return defaultFallback;
                                    let c = info.c, w = info.w, h = info.h;

                                    // Universal Scan:
                                    // Start just below sticky navbar (0.15) down to 0.96
                                    // Works universally on Phones, Tablets, Foldables, and Any user scroll!
                                    let startY = Math.floor(h * 0.15);
                                    let endY = Math.floor(h * 0.96);
                                    let scanH = endY - startY + 1;
                                    let step = Math.max(1, Math.floor(h * 0.003));

                                    let colCanvas = document.createElement('canvas');
                                    colCanvas.width = 4;
                                    colCanvas.height = scanH;
                                    let colCtx = colCanvas.getContext('2d', { willReadFrequently: true });
                                    if (!colCtx) return 0.915;

                                    // Blit 4 vertical columns:
                                    // Col 0: X = 0.20 (Big left)
                                    colCtx.drawImage(c, Math.floor(w * 0.20), startY, 1, scanH, 0, 0, 1, scanH);
                                    // Col 1: X = 0.28 (Big center)
                                    colCtx.drawImage(c, Math.floor(w * 0.28), startY, 1, scanH, 1, 0, 1, scanH);
                                    // Col 2: X = 0.72 (Small center)
                                    colCtx.drawImage(c, Math.floor(w * 0.72), startY, 1, scanH, 2, 0, 1, scanH);
                                    // Col 3: X = 0.80 (Small right)
                                    colCtx.drawImage(c, Math.floor(w * 0.80), startY, 1, scanH, 3, 0, 1, scanH);

                                    let imgData = colCtx.getImageData(0, 0, 4, scanH).data;
                                    let dualMatches = []; // Y where BOTH Big (orange on left) AND Small (blue on right) exist
                                    let singleMatches = []; // Y where target choice button exists

                                    for (let row = 0; row < scanH; row += step) {
                                        let actualY = startY + row;
                                        let yPct = actualY / h;
                                        let rowOffset = row * 16;

                                        let rBigA = imgData[rowOffset], gBigA = imgData[rowOffset + 1], bBigA = imgData[rowOffset + 2];
                                        let rBigB = imgData[rowOffset + 4], gBigB = imgData[rowOffset + 5], bBigB = imgData[rowOffset + 6];
                                        let rSmallA = imgData[rowOffset + 8], gSmallA = imgData[rowOffset + 9], bSmallA = imgData[rowOffset + 10];
                                        let rSmallB = imgData[rowOffset + 12], gSmallB = imgData[rowOffset + 13], bSmallB = imgData[rowOffset + 14];

                                        // Big orange: bright active OR dimmed lock state
                                        let isBigOrange = ((rBigA >= 170 && gBigA >= 95 && bBigA <= 105 && rBigA > bBigA + 30) || (rBigA >= 105 && gBigA >= 55 && bBigA <= 90 && rBigA > bBigA + 20)) ||
                                                          ((rBigB >= 170 && gBigB >= 95 && bBigB <= 105 && rBigB > bBigB + 30) || (rBigB >= 105 && gBigB >= 55 && bBigB <= 90 && rBigB > bBigB + 20));

                                        // Small blue: bright active OR dimmed lock state
                                        let isSmallBlue = ((bSmallA >= 150 && gSmallA >= 95 && rSmallA <= 150 && bSmallA > rSmallA + 20) || (bSmallA >= 95 && gSmallA >= 55 && rSmallA <= 105 && bSmallA > rSmallA + 15)) ||
                                                          ((bSmallB >= 150 && gSmallB >= 95 && rSmallB <= 150 && bSmallB > rSmallB + 20) || (bSmallB >= 95 && gSmallB >= 55 && rSmallB <= 105 && bSmallB > rSmallB + 15));

                                        // Dual match: Big & Small are side-by-side on the EXACT SAME row!
                                        if (isBigOrange && isSmallBlue) {
                                            dualMatches.push(yPct);
                                        }
                                        if (isBig ? isBigOrange : isSmallBlue) {
                                            singleMatches.push(yPct);
                                        }
                                    }

                                    // Prioritize dualMatches: 100% guaranteed to be the Big/Small pair row!
                                    // Fallback to singleMatches if dual match didn't catch both
                                    let chosenList = (dualMatches.length >= 1) ? dualMatches : singleMatches;

                                    if (chosenList.length > 0) {
                                        let bestRun = [chosenList[0]];
                                        let currentRun = [chosenList[0]];
                                        for (let i = 1; i < chosenList.length; i++) {
                                            if (chosenList[i] - chosenList[i - 1] <= 0.015) {
                                                currentRun.push(chosenList[i]);
                                            } else {
                                                if (currentRun.length > bestRun.length) bestRun = currentRun;
                                                currentRun = [chosenList[i]];
                                            }
                                        }
                                        if (currentRun.length > bestRun.length) bestRun = currentRun;

                                        let centerY = (bestRun[0] + bestRun[bestRun.length - 1]) / 2.0;
                                        console.log('[tigerExecuteBet] Dynamic button finder: Found ' + (isBig ? 'BIG' : 'SMALL') + ' at Y = ' + centerY.toFixed(3) + ' (range: ' + bestRun[0].toFixed(3) + '..' + bestRun[bestRun.length - 1].toFixed(3) + ') via universal-scan');
                                        return parseFloat(centerY.toFixed(3));
                                    }
                                } catch(e) {
                                    console.warn('[tigerExecuteBet] findButtonCoordinates error:', e);
                                }
                                let defaultFallback = isTablet ? 0.345 : 0.915;
                                console.log('[tigerExecuteBet] Dynamic button finder: Defaulting to fallback Y = ' + defaultFallback);
                                return defaultFallback;
                            }

                            function findColorButtonCoordinates(isGreen) {
                                try {
                                    let info = getCanvasAndContext();
                                    if (!info) return 0.655;
                                    let c = info.c, w = info.w, h = info.h;

                                    let startY = Math.floor(h * 0.15);
                                    let endY = Math.floor(h * 0.90);
                                    let scanH = endY - startY + 1;
                                    let step = Math.max(1, Math.floor(h * 0.003));

                                    let colCanvas = document.createElement('canvas');
                                    colCanvas.width = 2;
                                    colCanvas.height = scanH;
                                    let colCtx = colCanvas.getContext('2d', { willReadFrequently: true });
                                    if (!colCtx) return 0.655;

                                    // Col 0: X = 0.20 (Green)
                                    colCtx.drawImage(c, Math.floor(w * 0.20), startY, 1, scanH, 0, 0, 1, scanH);
                                    // Col 1: X = 0.75 (Red)
                                    colCtx.drawImage(c, Math.floor(w * 0.75), startY, 1, scanH, 1, 0, 1, scanH);

                                    let imgData = colCtx.getImageData(0, 0, 2, scanH).data;
                                    let dualMatches = [];
                                    let singleMatches = [];

                                    for (let row = 0; row < scanH; row += step) {
                                        let actualY = startY + row;
                                        let yPct = actualY / h;
                                        let rowOffset = row * 8;

                                        let rG = imgData[rowOffset], gG = imgData[rowOffset + 1], bG = imgData[rowOffset + 2];
                                        let rR = imgData[rowOffset + 4], gR = imgData[rowOffset + 5], bR = imgData[rowOffset + 6];

                                        let isGreenMatch = (gG >= 80 && gG > rG + 20 && gG > bG + 15) || (gG >= 150 && gG > rG + 40);
                                        let isRedMatch = (rR >= 110 && rR > gR + 30 && rR > bR + 30) || (rR >= 180 && rR > gR + 50);

                                        if (isGreenMatch && isRedMatch) {
                                            dualMatches.push(yPct);
                                        }
                                        if (isGreen ? isGreenMatch : isRedMatch) {
                                            singleMatches.push(yPct);
                                        }
                                    }

                                    let chosenList = (dualMatches.length >= 1) ? dualMatches : singleMatches;
                                    if (chosenList.length > 0) {
                                        let bestRun = [chosenList[0]];
                                        let currentRun = [chosenList[0]];
                                        for (let i = 1; i < chosenList.length; i++) {
                                            if (chosenList[i] - chosenList[i - 1] <= 0.015) {
                                                currentRun.push(chosenList[i]);
                                            } else {
                                                if (currentRun.length > bestRun.length) bestRun = currentRun;
                                                currentRun = [chosenList[i]];
                                            }
                                        }
                                        if (currentRun.length > bestRun.length) bestRun = currentRun;

                                        let centerY = (bestRun[0] + bestRun[bestRun.length - 1]) / 2.0;
                                        console.log('[tigerExecuteBet] Dynamic color button finder: Found ' + (isGreen ? 'GREEN' : 'RED') + ' at Y = ' + centerY.toFixed(3));
                                        return parseFloat(centerY.toFixed(3));
                                    }
                                } catch(e) {
                                    console.warn('[tigerExecuteBet] findColorButtonCoordinates error:', e);
                                }
                                let bsY = findButtonCoordinates(true);
                                let fallbackY = parseFloat((bsY - 0.278).toFixed(3));
                                console.log('[tigerExecuteBet] Dynamic color button fallback Y = ' + fallbackY);
                                return fallbackY > 0.15 ? fallbackY : 0.655;
                            }

                            proceedClickBetButton();

                            function proceedClickBetButton() {
                                let clickXPct = isColorBet ? (isGreen ? 0.20 : 0.75) : (isBig ? 0.28 : 0.72);
                                let clickYPct = isColorBet ? findColorButtonCoordinates(isGreen) : findButtonCoordinates(isBig);
                                console.log('[tigerExecuteBet] Step 1: Clicking ' + upperChoice + ' at (' + clickXPct + ', ' + clickYPct + ')');
                                dispatchTap(clickXPct, clickYPct);

                                // 2. Ensure sheet is open
                                let openCheckCount = 0;
                                function waitForSheet(callback) {
                                    openCheckCount++;
                                    if (isBottomSheetOpen()) {
                                        console.log('[tigerExecuteBet] Bottom sheet confirmed OPEN!');
                                        callback(true);
                                        return;
                                    }
                                    if (openCheckCount >= 4) {
                                        console.warn('[tigerExecuteBet] Bottom sheet not opened yet, re-scanning button position...');
                                        let retryYPct = isColorBet ? findColorButtonCoordinates(isGreen) : findButtonCoordinates(isBig);
                                        dispatchTap(clickXPct, retryYPct);
                                        setTimeout(function() {
                                            if (isBottomSheetOpen()) {
                                                callback(true);
                                            } else {
                                                // Scroll page if buttons are off-screen
                                                if (window.VipRobotBridge && window.VipRobotBridge.scrollToBettingArea) {
                                                    window.VipRobotBridge.scrollToBettingArea();
                                                }
                                                setTimeout(function() {
                                                    let finalY = isColorBet ? findColorButtonCoordinates(isGreen) : findButtonCoordinates(isBig);
                                                    dispatchTap(clickXPct, finalY);
                                                    setTimeout(function() {
                                                        callback(isBottomSheetOpen());
                                                    }, 250);
                                                }, 300);
                                            }
                                        }, 250);
                                        return;
                                    }
                                    setTimeout(function() { waitForSheet(callback); }, 120);
                                }

                                waitForSheet(function(sheetReady) {
                                    if (!sheetReady) {
                                        console.error('[tigerExecuteBet] Aborting bet: Bottom sheet never opened!');
                                        if (window.VipRobotBridge && window.VipRobotBridge.onBetAttemptFailed) {
                                            window.VipRobotBridge.onBetAttemptFailed('Sheet did not open');
                                        }
                                        return;
                                    }

                                    // GATEKEEPER VERIFICATION:
                                    // Verify that the opened bottom sheet matches the selected choice (BIG, SMALL, RED, GREEN)!
                                    function verifyBottomSheetChoice() {
                                        try {
                                            let info = getCanvasAndContext();
                                            if (!info) return true;
                                            let c = info.c, ctx = info.ctx, w = info.w, h = info.h;

                                            // Top badge of sheet is around Y = 0.50 to 0.75
                                            let hasOrange = false;
                                            let hasBlue = false;
                                            let hasRed = false;
                                            let hasGreen = false;

                                            let startY = Math.floor(h * 0.50);
                                            let scanH = Math.floor(h * 0.25);
                                            let badgeCanvas = document.createElement('canvas');
                                            badgeCanvas.width = 1;
                                            badgeCanvas.height = scanH;
                                            let badgeCtx = badgeCanvas.getContext('2d', { willReadFrequently: true });
                                            if (badgeCtx) {
                                                badgeCtx.drawImage(c, Math.floor(w * 0.50), startY, 1, scanH, 0, 0, 1, scanH);
                                                let p = badgeCtx.getImageData(0, 0, 1, scanH).data;
                                                for (let i = 0; i < p.length; i += 4) {
                                                    let r = p[i], g = p[i+1], b = p[i+2];
                                                    if (r >= 185 && g >= 110 && g <= 200 && b <= 95 && r > b + 40) hasOrange = true;
                                                    if (b >= 165 && g >= 110 && g <= 200 && r <= 140 && b > r + 30) hasBlue = true;
                                                    if (r >= 200 && g <= 90 && b <= 90 && r > g + 80) hasRed = true;
                                                    if (g >= 160 && r <= 95 && b <= 95 && g > r + 50) hasGreen = true;
                                                }
                                            }

                                            if (upperChoice === 'BIG') {
                                                if (!hasOrange && (hasRed || hasGreen)) {
                                                    console.error('[tigerExecuteBet] CRITICAL REJECTION: Expected BIG, but detected Red/Green sheet!');
                                                    return false;
                                                }
                                            } else if (upperChoice === 'SMALL') {
                                                if (!hasBlue && (hasRed || hasGreen)) {
                                                    console.error('[tigerExecuteBet] CRITICAL REJECTION: Expected SMALL, but detected Red/Green sheet!');
                                                    return false;
                                                }
                                            } else if (upperChoice === 'RED') {
                                                if (!hasRed && (hasOrange || hasBlue || hasGreen)) {
                                                    console.error('[tigerExecuteBet] CRITICAL REJECTION: Expected RED, but detected wrong sheet!');
                                                    return false;
                                                }
                                            } else if (upperChoice === 'GREEN') {
                                                if (!hasGreen && (hasOrange || hasBlue || hasRed)) {
                                                    console.error('[tigerExecuteBet] CRITICAL REJECTION: Expected GREEN, but detected wrong sheet!');
                                                    return false;
                                                }
                                            }
                                        } catch(e) {
                                            console.warn('[tigerExecuteBet] verifyBottomSheetChoice error:', e);
                                        }
                                        return true;
                                    }

                                    if (!verifyBottomSheetChoice()) {
                                        console.error('[tigerExecuteBet] ABORTING: Wrong sheet opened! Dismissing sheet immediately to prevent wrong bet...');
                                        dispatchTap(0.167, cancelBtnY); // Cancel button on sheet
                                        if (window.VipRobotBridge && window.VipRobotBridge.onBetAttemptFailed) {
                                            window.VipRobotBridge.onBetAttemptFailed('Wrong sheet opened (prevented wrong bet)');
                                        }
                                        return;
                                    }

                                    // Bottom sheet controls:
                                    const baseChips = {
                                        1: { x: 0.411, y: baseChipY },
                                        5: { x: 0.511, y: baseChipY },
                                        10: { x: 0.611, y: baseChipY },
                                        20: { x: 0.711, y: baseChipY },
                                        50: { x: 0.811, y: baseChipY },
                                        100: { x: 0.911, y: baseChipY }
                                    };
                                    const multChips = {
                                        1: { x: 0.083, y: multChipY },
                                        5: { x: 0.181, y: multChipY },
                                        10: { x: 0.278, y: multChipY },
                                        20: { x: 0.375, y: multChipY },
                                        50: { x: 0.472, y: multChipY },
                                        100: { x: 0.569, y: multChipY }
                                    };
                                    const minusBtn = { x: 0.472, y: qtyBoxY };
                                    const plusBtn = { x: 0.861, y: qtyBoxY };

                                    const cleanBases = [100, 50, 20, 10, 5, 1];
                                    const cleanMults = [100, 50, 20, 10, 5, 1];

                                    function getCleanChipPair(target) {
                                        for (let b of cleanBases) {
                                            if (target % b === 0) {
                                                let m = Math.floor(target / b);
                                                if (cleanMults.includes(m)) {
                                                    return { base: b, mult: m };
                                                }
                                            }
                                        }
                                        return null;
                                    }

                                    let chipPair = getCleanChipPair(targetQty);
                                    if (chipPair) {
                                        console.log('[tigerExecuteBet] Step 2: Clean chip match for ₹' + targetQty + ' -> Base ' + chipPair.base + ', Mult X' + chipPair.mult);
                                        dispatchTap(baseChips[chipPair.base].x, baseChips[chipPair.base].y);
                                        setTimeout(function() {
                                            dispatchTap(multChips[chipPair.mult].x, multChips[chipPair.mult].y);
                                            setTimeout(submitTotalAmount, 150);
                                        }, 100);
                                    } else {
                                        console.log('[tigerExecuteBet] Step 2: Direct keyboard entry for ₹' + targetQty + ' (fast direct input)...');
                                        // Ensure Base 1 and Mult X1
                                        dispatchTap(baseChips[1].x, baseChips[1].y);
                                        setTimeout(function() {
                                            dispatchTap(multChips[1].x, multChips[1].y);
                                            setTimeout(function() {
                                                // Scan exact quantity box position dynamically
                                                let targetBoxY = findQuantityBoxY();
                                                console.log('[tigerExecuteBet] Tapping Quantity Box at (0.74, ' + targetBoxY + ')');
                                                dispatchTap(0.74, targetBoxY);
                                                setTimeout(function() {
                                                    // Set DOM inputs directly
                                                    let list = [];
                                                    function scan(root) {
                                                        if (!root) return;
                                                        root.querySelectorAll('input, textarea').forEach(el => list.push(el));
                                                        root.querySelectorAll('*').forEach(el => { if (el.shadowRoot) scan(el.shadowRoot); });
                                                    }
                                                    scan(document);
                                                    list.forEach(function(inp) {
                                                        try {
                                                            inp.focus();
                                                            inp.value = '' + targetQty;
                                                            inp.dispatchEvent(new Event('input', { bubbles: true }));
                                                            inp.dispatchEvent(new Event('change', { bubbles: true }));
                                                        } catch(_) {}
                                                    });

                                                    // Prepare completion callback from native Android
                                                    let submitted = false;
                                                    window._onQuantityEntered = function() {
                                                        if (submitted) return;
                                                        submitted = true;
                                                        console.log('[tigerExecuteBet] Native quantity entry completed for ₹' + targetQty + '. Submitting...');
                                                        setTimeout(submitTotalAmount, 80);
                                                    };

                                                    // Fallback in case native callback didn't fire
                                                    let maxWait = 350 + (('' + targetQty).length * 60) + 250;
                                                    setTimeout(function() {
                                                        if (!submitted) {
                                                            submitted = true;
                                                            console.log('[tigerExecuteBet] Quantity entry timeout fallback. Submitting...');
                                                            submitTotalAmount();
                                                        }
                                                    }, maxWait);

                                                    // Dispatch native Android KeyEvents with sequenced digits
                                                    if (window.VipRobotBridge && window.VipRobotBridge.enterDirectQuantity) {
                                                        window.VipRobotBridge.enterDirectQuantity(targetQty);
                                                    }
                                                }, 150);
                                            }, 100);
                                        }, 100);
                                    }

                                    function submitTotalAmount() {
                                        totalAmountBtnY = findTotalAmountButtonY();
                                        cancelBtnY = totalAmountBtnY;
                                        console.log('[tigerExecuteBet] Step 3: Tapping Total Amount at (0.667, ' + totalAmountBtnY + ')');
                                        dispatchTap(0.667, totalAmountBtnY);

                                        // Step 4: Verification loop
                                        let verifyStart = Date.now();
                                        let verified = false;

                                        let verifyInterval = setInterval(function() {
                                            let elapsed = Date.now() - verifyStart;
                                            let toastVisible = isOperationSuccessVisible();
                                            let sheetStillOpen = isBottomSheetOpen();

                                            // Verified if success toast appeared, or sheet successfully closed after tap
                                            if (toastVisible || (!sheetStillOpen && elapsed >= 400)) {
                                                clearInterval(verifyInterval);
                                                verified = true;
                                                console.log('[tigerExecuteBet] BET PLACEMENT STRICTLY VERIFIED!');
                                                if (window.VipRobotBridge && window.VipRobotBridge.onBetPlacedSuccess) {
                                                    window.VipRobotBridge.onBetPlacedSuccess(choice, targetQty);
                                                }
                                                if (typeof pollGameData === 'function') {
                                                    setTimeout(pollGameData, 500);
                                                }
                                                return;
                                            }

                                            // If after 900ms the sheet is still open, retry tapping Total Amount once
                                            if (elapsed >= 900 && elapsed < 1000 && sheetStillOpen) {
                                                console.log('[tigerExecuteBet] Sheet still open, retrying Total Amount tap...');
                                                totalAmountBtnY = findTotalAmountButtonY();
                                                dispatchTap(0.667, totalAmountBtnY);
                                            }

                                            if (elapsed >= 2500) {
                                                clearInterval(verifyInterval);
                                                if (!verified) {
                                                    if (!sheetStillOpen) {
                                                        // Sheet closed, bet registered
                                                        console.log('[tigerExecuteBet] Sheet closed, bet placed!');
                                                        if (window.VipRobotBridge && window.VipRobotBridge.onBetPlacedSuccess) {
                                                            window.VipRobotBridge.onBetPlacedSuccess(choice, targetQty);
                                                        }
                                                    } else {
                                                        console.warn('[tigerExecuteBet] Bet verification timed out!');
                                                        // Dismiss open sheet via Cancel so screen is clean
                                                        dispatchTap(0.167, cancelBtnY);
                                                        if (window.VipRobotBridge && window.VipRobotBridge.onBetAttemptFailed) {
                                                            window.VipRobotBridge.onBetAttemptFailed('Bet not confirmed');
                                                        }
                                                    }
                                                }
                                            }
                                        }, 80);
                                    }
                                });
                            }
                        }

                        if (hadPopup) {
                            setTimeout(placeBetFlow, 250);
                        } else {
                            placeBetFlow();
                        }
                    } catch(e) {
                        console.error('[tigerExecuteBet] Error:', e);
                        if (window.VipRobotBridge && window.VipRobotBridge.onBetAttemptFailed) {
                            window.VipRobotBridge.onBetAttemptFailed('' + (e.message || e));
                        }
                    }
                };

                if (!window._tigerTabClickAttached) {
                    window._tigerTabClickAttached = true;
                    document.addEventListener('click', function(e) {
                        try {
                            let el = e.target;
                            while (el && el !== document.body) {
                                let txt = (el.innerText || el.textContent || '').trim();
                                if (/^My\s*history$/i.test(txt) || (/My\s*history/i.test(txt) && txt.length <= 15)) {
                                    window._lastMyHistoryActive = true;
                                    if (window.VipRobotBridge && window.VipRobotBridge.onMyHistoryTabStateChanged) {
                                        window.VipRobotBridge.onMyHistoryTabStateChanged(true);
                                    }
                                    break;
                                } else if (/^Game\s*history$/i.test(txt) || /^Chart$/i.test(txt) || (/Game\s*history/i.test(txt) && txt.length <= 15)) {
                                    window._lastMyHistoryActive = false;
                                    if (window.VipRobotBridge && window.VipRobotBridge.onMyHistoryTabStateChanged) {
                                        window.VipRobotBridge.onMyHistoryTabStateChanged(false);
                                    }
                                    break;
                                }
                                el = el.parentElement;
                            }
                        } catch(e) {}
                    }, true);
                }

                // Tab auto-selection removed

                // Smooth polling for countdown and period (1000ms)
                setInterval(function() {
                    enableFlutterSemantics();
                    scrapeGameInfo();
                }, 1000);

                

                // Balance polling (2000ms)
                setInterval(function() {
                    enableFlutterSemantics();
                    scrapeBalance();
                }, 2000);

                scrapeBalance();
                scrapeGameInfo();
            })();
        """.trimIndent()

        binding.webView.evaluateJavascript(script, null)
    }

    /**
     * JavaScript Bridge exposing methods to in-page scripts.
     */
    inner class VipRobotBridge {
        @JavascriptInterface
        fun onBalanceScraped(balanceStr: String) {
            runOnUiThread {
                handleScrapedBalance(balanceStr)
            }
        }

        @JavascriptInterface
        fun onPeriodDetected(periodId: String, timeLeftSec: Int, modeName: String) {
            runOnUiThread {
                handlePeriodDetected(periodId, timeLeftSec, modeName)
            }
        }

        @JavascriptInterface
        fun onBetPlacedSuccess(choice: String, amount: Double) {
            runOnUiThread {
                handleBetPlacedSuccess(choice, amount)
            }
        }

        @JavascriptInterface
        fun onBetResultResolved(periodId: String, isWin: Boolean, profitAmt: Double) {
            runOnUiThread {
                resolveBetResult(periodId, isWin, profitAmt)
            }
        }

        @JavascriptInterface
        fun onGameResultDetectedWithNumber(periodId: String, resultChoice: String, resultColor: String, ballNum: Int) {
            runOnUiThread {
                handleGameResult(periodId, resultChoice, resultColor, ballNum)
            }
        }

        @JavascriptInterface
        fun onGameHistoryBatch(batchJson: String) {
            runOnUiThread {
                handleGameHistoryBatch(batchJson)
            }
        }

        @JavascriptInterface
        fun onMyHistoryScraped(batchJson: String) {
            runOnUiThread {
                handleMyHistoryScraped(batchJson)
            }
        }

        @JavascriptInterface
        fun onMyHistoryTabStateChanged(isMyHistory: Boolean) {
            runOnUiThread {
                isMyHistoryTabActiveOnSite = isMyHistory
            }
        }

        @JavascriptInterface
        fun dispatchNativeClick(xPercent: Float, yPercent: Float) {
            runOnUiThread {
                if (!isBetPlacingInProgress) {
                    Log.w("PantherTouch", "Blocked dispatchNativeClick: bot is not placing a bet! ($xPercent, $yPercent)")
                    return@runOnUiThread
                }
                val w = binding.webView.width
                val h = binding.webView.height
                if (w > 0 && h > 0) {
                    val cx = w * xPercent
                    val cy = h * yPercent
                    val downTime = SystemClock.uptimeMillis()
                    val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, cx, cy, 0)
                    isBotDispatchedTouch = true
                    binding.webView.dispatchTouchEvent(down)
                    down.recycle()
                    binding.webView.postDelayed({
                        val upTime = SystemClock.uptimeMillis()
                        val up = MotionEvent.obtain(downTime, upTime, MotionEvent.ACTION_UP, cx, cy, 0)
                        isBotDispatchedTouch = true
                        binding.webView.dispatchTouchEvent(up)
                        up.recycle()
                        isBotDispatchedTouch = false
                    }, 50)
                }
            }
        }

        @JavascriptInterface
        fun enterDirectQuantity(qty: Int) {
            runOnUiThread {
                try {
                    val str = qty.toString()
                    val now = SystemClock.uptimeMillis()

                    // 1. Move to End and clear backwards
                    binding.webView.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MOVE_END))
                    binding.webView.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MOVE_END))
                    for (i in 0 until 6) {
                        binding.webView.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
                        binding.webView.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
                    }

                    // 2. Select all via Ctrl+A and delete
                    binding.webView.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_CTRL_LEFT, 0, KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON))
                    binding.webView.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A, 0, KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON))
                    binding.webView.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_A, 0, KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON))
                    binding.webView.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_CTRL_LEFT, 0, 0))
                    binding.webView.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
                    binding.webView.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))

                    // 3. Dispatch each target digit keyevent sequentially with 45ms pacing
                    for (i in str.indices) {
                        val c = str[i]
                        val code = when (c) {
                            '0' -> KeyEvent.KEYCODE_0
                            '1' -> KeyEvent.KEYCODE_1
                            '2' -> KeyEvent.KEYCODE_2
                            '3' -> KeyEvent.KEYCODE_3
                            '4' -> KeyEvent.KEYCODE_4
                            '5' -> KeyEvent.KEYCODE_5
                            '6' -> KeyEvent.KEYCODE_6
                            '7' -> KeyEvent.KEYCODE_7
                            '8' -> KeyEvent.KEYCODE_8
                            '9' -> KeyEvent.KEYCODE_9
                            else -> -1
                        }
                        if (code != -1) {
                            mainHandler.postDelayed({
                                binding.webView.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
                                binding.webView.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
                            }, (50 + (i * 45)).toLong())
                        }
                    }

                    // 4. DO NOT SEND KEYCODE_ENTER!
                    // After all digits are dispatched + 100ms, dismiss keyboard and notify JS that quantity is complete!
                    val finishDelay = (50 + (str.length * 45) + 100).toLong()
                    mainHandler.postDelayed({
                        dismissKeyboardNative()
                        binding.webView.evaluateJavascript("if (typeof window._onQuantityEntered === 'function') window._onQuantityEntered();", null)
                    }, finishDelay)
                } catch (e: Exception) {
                    Log.e("PantherBet", "enterDirectQuantity error", e)
                }
            }
        }

        @JavascriptInterface
        fun dismissKeyboard() {
            runOnUiThread {
                dismissKeyboardNative()
            }
        }

        @JavascriptInterface
        fun scrollToBettingArea() {
            runOnUiThread {
                try {
                    val w = binding.webView.width.toFloat()
                    val h = binding.webView.height.toFloat()
                    if (w <= 0 || h <= 0) return@runOnUiThread

                    val midX = w * 0.5f
                    val startY = h * 0.35f
                    val endY = h * 0.80f
                    val downTime = SystemClock.uptimeMillis()
                    val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, midX, startY, 0)
                    isBotDispatchedTouch = true
                    binding.webView.dispatchTouchEvent(down)
                    down.recycle()

                    for (step in 1..8) {
                        val curY = startY + (endY - startY) * (step / 8f)
                        val moveTime = downTime + step * 25
                        val move = MotionEvent.obtain(downTime, moveTime, MotionEvent.ACTION_MOVE, midX, curY, 0)
                        isBotDispatchedTouch = true
                        binding.webView.dispatchTouchEvent(move)
                        move.recycle()
                    }

                    val upTime = downTime + 220
                    val up = MotionEvent.obtain(downTime, upTime, MotionEvent.ACTION_UP, midX, endY, 0)
                    isBotDispatchedTouch = true
                    binding.webView.dispatchTouchEvent(up)
                    up.recycle()
                    isBotDispatchedTouch = false
                } catch (_: Exception) {
                    isBotDispatchedTouch = false
                }
            }
        }

        @JavascriptInterface
        fun onBetAttemptFailed(reason: String) {
            runOnUiThread {
                handleBetAttemptFailed(reason)
            }
        }
    }

    private fun dismissKeyboardNative() {
        try {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            val token = currentFocus?.windowToken ?: binding.webView.windowToken ?: window.decorView.windowToken
            imm?.hideSoftInputFromWindow(token, 0)
            imm?.hideSoftInputFromWindow(binding.webView.windowToken, 0)
            imm?.hideSoftInputFromWindow(binding.root.windowToken, 0)
            imm?.hideSoftInputFromWindow(window.decorView.windowToken, 0)
            androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)?.hide(androidx.core.view.WindowInsetsCompat.Type.ime())
            binding.root.clearFocus()
            binding.webView.clearFocus()

            val insets = androidx.core.view.ViewCompat.getRootWindowInsets(window.decorView)
            val isImeVisible = insets?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true
            if (isImeVisible) {
                val now = SystemClock.uptimeMillis()
                binding.webView.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK, 0))
                binding.webView.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK, 0))
            }
        } catch (_: Exception) {}
    }

    /**
     * Temporary Dev Inspector Bridge for capturing network calls and console logs.
     */
    inner class DevInspectorBridge {
        @JavascriptInterface
        fun logNetwork(method: String, url: String, status: Int, duration: String, error: String, isXhr: Boolean) {
            DevInspectorManager.recordNetwork(method, url, status, duration, error, isXhr)
        }

        @JavascriptInterface
        fun logConsole(level: String, message: String, source: String) {
            DevInspectorManager.recordConsole(level, message, source)
        }

        @JavascriptInterface
        fun getClipboardText(): String {
            return try {
                val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = clipboard.primaryClip
                if (clip != null && clip.itemCount > 0) {
                    clip.getItemAt(0).text?.toString() ?: ""
                } else {
                    ""
                }
            } catch (_: Exception) {
                ""
            }
        }

        @JavascriptInterface
        fun setClipboardText(text: String) {
            runOnUiThread {
                try {
                    val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText("web", text)
                    clipboard.setPrimaryClip(clip)
                } catch (_: Exception) {}
            }
        }
    }

    private fun getClipboardFallbackScript(): String {
        return """
            (function() {
                try {
                    if (!navigator.clipboard) {
                        try {
                            navigator.clipboard = {};
                        } catch(e) {}
                    }
                    if (navigator.clipboard) {
                        if (typeof navigator.clipboard.readText !== 'function' || !navigator.clipboard.readText.__safeFallback) {
                            const originalReadText = typeof navigator.clipboard.readText === 'function' ?
                                navigator.clipboard.readText.bind(navigator.clipboard) : null;
                            const safeReadText = async function() {
                                try {
                                    if (originalReadText) {
                                        return await originalReadText();
                                    }
                                    return "";
                                } catch (error) {
                                    console.warn('[ClipboardFallback] readText denied/fallback:', error ? error.name : 'err');
                                    return "";
                                }
                            };
                            safeReadText.__safeFallback = true;
                            navigator.clipboard.readText = safeReadText;
                        }

                        if (typeof navigator.clipboard.read !== 'function' || !navigator.clipboard.read.__safeFallback) {
                            const originalRead = typeof navigator.clipboard.read === 'function' ?
                                navigator.clipboard.read.bind(navigator.clipboard) : null;
                            const safeRead = async function() {
                                try {
                                    if (originalRead) {
                                        return await originalRead();
                                    }
                                    return [];
                                } catch (error) {
                                    return [];
                                }
                            };
                            safeRead.__safeFallback = true;
                            navigator.clipboard.read = safeRead;
                        }

                        if (typeof navigator.clipboard.writeText !== 'function' || !navigator.clipboard.writeText.__safeFallback) {
                            const originalWrite = typeof navigator.clipboard.writeText === 'function' ?
                                navigator.clipboard.writeText.bind(navigator.clipboard) : null;
                            const safeWrite = async function(text) {
                                try {
                                    if (originalWrite) {
                                        return await originalWrite(text);
                                    }
                                    return Promise.resolve();
                                } catch (error) {
                                    return Promise.resolve();
                                }
                            };
                            safeWrite.__safeFallback = true;
                            navigator.clipboard.writeText = safeWrite;
                        }

                        console.log('[ClipboardFallback] Complete safe clipboard fallback installed');
                    }
                } catch (error) {
                    console.error('[ClipboardFallback] Installation failed:', error);
                }
            })();
        """.trimIndent()
    }

    private fun injectClipboardFallback() {
        binding.webView.evaluateJavascript(getClipboardFallbackScript(), null)
    }

    private fun updateDevInspectorBadges() {
        val totalNet = DevInspectorManager.getTotalNetworkCount()
        val failedNet = DevInspectorManager.getFailedNetworkCount()
        val totalConsole = DevInspectorManager.getTotalConsoleCount()
        val errorConsole = DevInspectorManager.getConsoleErrorCount()

        binding.btnDevNetwork.text = if (failedNet > 0) "🌐 Net: $totalNet ($failedNet err)" else "🌐 Net: $totalNet"
        binding.btnDevNetwork.setTextColor(if (failedNet > 0) Color.parseColor("#EF4444") else Color.parseColor("#38BDF8"))

        binding.btnDevConsole.text = if (errorConsole > 0) "💻 Log: $totalConsole ($errorConsole err)" else "💻 Log: $totalConsole"
        binding.btnDevConsole.setTextColor(if (errorConsole > 0) Color.parseColor("#EF4444") else Color.parseColor("#F59E0B"))
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDevInspector() {
        try {
            WebView.setWebContentsDebuggingEnabled(true)
        } catch (_: Exception) {}

        binding.btnDevNetwork.setOnClickListener {
            DevNetworkBottomSheet.newInstance().show(supportFragmentManager, DevNetworkBottomSheet.TAG)
        }

        binding.btnDevConsole.setOnClickListener {
            DevConsoleBottomSheet.newInstance().show(supportFragmentManager, DevConsoleBottomSheet.TAG)
        }

        DevInspectorManager.setListener {
            runOnUiThread {
                updateDevInspectorBadges()
            }
        }
        updateDevInspectorBadges()

        val bar = binding.layoutDevFloatingBar
        var dX = 0f
        var dY = 0f
        var isDrag = false
        var startDownX = 0f
        var startDownY = 0f

        val devDragTouchListener = View.OnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    bar.parent?.requestDisallowInterceptTouchEvent(true)
                    dX = bar.x - event.rawX
                    dY = bar.y - event.rawY
                    startDownX = event.rawX
                    startDownY = event.rawY
                    isDrag = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dist = Math.hypot((event.rawX - startDownX).toDouble(), (event.rawY - startDownY).toDouble())
                    if (dist > 10) isDrag = true
                    val parent = bar.parent as? View
                    if (parent != null) {
                        val margin = 8f * resources.displayMetrics.density
                        val maxX = (parent.width - bar.width - margin).coerceAtLeast(margin)
                        val maxY = (parent.height - bar.height - margin).coerceAtLeast(margin)
                        bar.x = (event.rawX + dX).coerceIn(margin, maxX)
                        bar.y = (event.rawY + dY).coerceIn(margin, maxY)
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    bar.parent?.requestDisallowInterceptTouchEvent(false)
                    true
                }
                else -> false
            }
        }

        binding.tvDevDragHandle.setOnTouchListener(devDragTouchListener)
    }

    private fun getDevInspectorScript(): String {
        return """
            (function() {
                if (window._pantherDevInjected) return;
                window._pantherDevInjected = true;

                function formatArg(a) {
                    if (a === null) return 'null';
                    if (a === undefined) return 'undefined';
                    if (typeof a === 'string') return a;
                    if (typeof a === 'number' || typeof a === 'boolean') return '' + a;
                    try {
                        var parts = [];
                        if (a.dartException) {
                            var de = a.dartException;
                            var deStr = (de && typeof de.toString === 'function') ? de.toString() : ('' + de);
                            if (deStr && deStr !== '[object Object]') parts.push('DartException: ' + deStr);
                            else {
                                try { parts.push('DartException: ' + JSON.stringify(de)); } catch(_) {}
                            }
                        }
                        if (a.name || a.message) {
                            parts.push((a.name ? a.name + ': ' : '') + (a.message || ''));
                        }
                        if (a.stack) {
                            var cleanSt = ('' + a.stack).split('\n').filter(function(l){ return l.trim().length > 0; }).slice(0, 4).join('\n');
                            parts.push('Stack:\n' + cleanSt);
                        }
                        if (parts.length > 0) return parts.join('\n');
                        var json = JSON.stringify(a);
                        if (json && json !== '{}') return json;
                    } catch(e) {}
                    return ('' + a) || '[Object]';
                }

                var origLog = console.log;
                var origWarn = console.warn;
                var origError = console.error;
                
                console.log = function() {
                    origLog.apply(console, arguments);
                    try {
                        var msg = Array.from(arguments).map(formatArg).join(' ');
                        if (window.DevInspectorBridge) window.DevInspectorBridge.logConsole('LOG', msg, '');
                    } catch(e) {}
                };

                console.warn = function() {
                    origWarn.apply(console, arguments);
                    try {
                        var msg = Array.from(arguments).map(formatArg).join(' ');
                        if (window.DevInspectorBridge) window.DevInspectorBridge.logConsole('WARN', msg, '');
                    } catch(e) {}
                };

                console.error = function() {
                    origError.apply(console, arguments);
                    try {
                        var msg = Array.from(arguments).map(formatArg).join(' ');
                        if (window.DevInspectorBridge) window.DevInspectorBridge.logConsole('ERROR', msg, '');
                    } catch(e) {}
                };

                window.addEventListener('error', function(e) {
                    try {
                        var src = (e.filename || '') + ':' + (e.lineno || 0);
                        var detail = '';
                        if (e.error) {
                            detail = formatArg(e.error);
                        }
                        if (!detail || detail === 'null' || detail === 'undefined' || detail === '[Object]') {
                            detail = e.message || 'Uncaught error';
                        }
                        if (window.DevInspectorBridge) window.DevInspectorBridge.logConsole('ERROR', detail, src);
                    } catch(err) {}
                });

                window.addEventListener('unhandledrejection', function(e) {
                    try {
                        var detail = formatArg(e.reason);
                        if (!detail) detail = 'Unhandled Promise Rejection';
                        if (window.DevInspectorBridge) window.DevInspectorBridge.logConsole('ERROR', 'UnhandledRejection:\n' + detail, '');
                    } catch(err) {}
                });

                // Auto-enable Flutter Web Semantics as soon as placeholder is injected into DOM
                try {
                    function tryClickPlaceholder() {
                        var ph = document.querySelector('flt-semantics-placeholder');
                        if (ph) {
                            ph.click();
                            ph.focus();
                            ph.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
                            return true;
                        }
                        return false;
                    }
                    if (!tryClickPlaceholder()) {
                        var semObserver = new MutationObserver(function() {
                            if (tryClickPlaceholder()) {
                                semObserver.disconnect();
                            }
                        });
                        var target = document.body || document.documentElement;
                        if (target) {
                            semObserver.observe(target, { childList: true, subtree: true });
                        } else {
                            window.addEventListener('DOMContentLoaded', function() {
                                if (!tryClickPlaceholder()) {
                                    semObserver.observe(document.body || document.documentElement, { childList: true, subtree: true });
                                }
                            });
                        }
                    }
                } catch(e) {}

                function tryInspectApiData(rawText) {
                    if (!rawText || typeof rawText !== 'string' || rawText.length < 5) return;
                    try {
                        var trimmed = rawText.trim();
                        var firstChar = trimmed.charAt(0);
                        if (firstChar !== '{' && firstChar !== '[') return;
                        var data = JSON.parse(trimmed);
                        inspectBalance(data);
                        inspectGameIssue(data);
                        inspectGameHistory(data);
                        inspectWinLoseTip(data);
                    } catch(e) {}
                }

                function inspectBalance(obj) {
                    if (!obj || typeof obj !== 'object') return;
                    if (obj.data && typeof obj.data === 'object') inspectBalance(obj.data);
                    if (obj.Data && typeof obj.Data === 'object') inspectBalance(obj.Data);
                    var keys = [
                        'TotalAmountStr', 'TotalAmount', 'AvaiAmountStr', 'AvaiAmount',
                        'totalAmountStr', 'totalAmount', 'avaiAmountStr', 'avaiAmount',
                        'userBalance', 'totalBalance', 'availableBalance', 'walletBalance', 'wallet_balance',
                        'balance', 'amount', 'money', 'userMoney', 'accountBalance'
                    ];
                    for (var i = 0; i < keys.length; i++) {
                        var k = keys[i];
                        if (obj[k] !== undefined && obj[k] !== null && obj[k] !== '') {
                            var v = parseFloat(String(obj[k]).replace(/,/g, ''));
                            if (!isNaN(v) && v >= 0 && v < 100000000) {
                                if (v > 1000000000) continue;
                                // Balance is strictly handled by decrypted KWG API (getbalance)
                                return;
                            }
                        }
                    }
                }

                function inspectGameIssue(obj) {
                    // Issue detection is strictly handled by decrypted KWG API (getcurrentissue)
                }

                function inspectGameHistory(obj) {
                    if (!obj || typeof obj !== 'object') return;
                    var list = (obj.Data && Array.isArray(obj.Data)) ? obj.Data :
                               ((obj.data && Array.isArray(obj.data)) ? obj.data :
                               (Array.isArray(obj) ? obj : (obj.list || obj.List || obj.rows || obj.Rows || null)));
                    if (list && Array.isArray(list) && list.length > 0) {
                        var formatted = [];
                        for (var i = 0; i < list.length; i++) {
                            var item = list[i];
                            if (!item || typeof item !== 'object') continue;
                            var pId = item.Issue || item.issue || item.issueNumber || item.periodNumber || item.period;
                            var code = item.Code !== undefined ? item.Code : (item.code !== undefined ? item.code : (item.number !== undefined ? item.number : null));
                            if (pId && code !== null) {
                                var cleanP = String(pId).trim();
                                var ballNum = parseInt(code);
                                if (!isNaN(ballNum) && ballNum >= 0 && ballNum <= 9) {
                                    var choice = (ballNum >= 5) ? "Big" : "Small";
                                    formatted.push({ p: cleanP, o: choice, n: ballNum });
                                }
                            }
                        }
                        if (formatted.length > 0 && window.VipRobotBridge && window.VipRobotBridge.onGameHistoryBatch) {
                            window.VipRobotBridge.onGameHistoryBatch(JSON.stringify(formatted.slice(0, 30)));
                        }
                        var first = list[0];
                        if (first && (first.issueNumber || first.periodNumber || first.period || first.stage || first.betAmount || first.selectType || first.colour || first.number || first.Issue)) {
                            if (window.VipRobotBridge && window.VipRobotBridge.onMyHistoryScraped) {
                                try {
                                    window.VipRobotBridge.onMyHistoryScraped(JSON.stringify(list.slice(0, 25)));
                                } catch(e) {}
                            }
                        }
                    }
                }

                function inspectWinLoseTip(obj) {
                    if (!obj || typeof obj !== 'object') return;
                    if (obj.Data && typeof obj.Data === 'object') inspectWinLoseTip(obj.Data);
                    if (obj.data && typeof obj.data === 'object') inspectWinLoseTip(obj.data);
                    if (obj.Issue !== undefined && (obj.WinAmount !== undefined || obj.WinAmountStr !== undefined)) {
                        var pId = String(obj.Issue).trim();
                        var winAmt = parseFloat(obj.WinAmountStr || obj.WinAmount || 0);
                        var isWin = winAmt > 0;
                        if (window.VipRobotBridge && window.VipRobotBridge.onBetResultResolved) {
                            window.VipRobotBridge.onBetResultResolved(pId, isWin, winAmt);
                        }
                    }
                }

                if (window.fetch) {
                    var origFetch = window.fetch;
                    window.fetch = function() {
                        var url = arguments[0];
                        if (typeof url === 'object' && url.url) url = url.url;
                        var start = Date.now();
                        var method = (arguments[1] && arguments[1].method) ? arguments[1].method : 'GET';
                        return origFetch.apply(this, arguments).then(function(res) {
                            var dur = (Date.now() - start) + 'ms';
                            try {
                                if (window.DevInspectorBridge) {
                                    window.DevInspectorBridge.logNetwork(method, '' + url, res.status, dur, res.ok ? '' : 'HTTP ' + res.status, true);
                                }
                            } catch(e) {}
                            try {
                                if (res.ok && res.clone) {
                                    res.clone().text().then(function(t) {
                                        tryInspectApiData(t);
                                    }).catch(function(){});
                                }
                            } catch(e) {}
                            return res;
                        }).catch(function(err) {
                            var dur = (Date.now() - start) + 'ms';
                            try {
                                if (window.DevInspectorBridge) {
                                    window.DevInspectorBridge.logNetwork(method, '' + url, 0, dur, '' + (err.message || err), true);
                                }
                            } catch(e) {}
                            throw err;
                        });
                    };
                }

                var origOpen = XMLHttpRequest.prototype.open;
                var origSend = XMLHttpRequest.prototype.send;
                XMLHttpRequest.prototype.open = function(method, url) {
                    this._devMethod = method;
                    this._devUrl = url;
                    this._devStart = Date.now();
                    return origOpen.apply(this, arguments);
                };
                XMLHttpRequest.prototype.send = function() {
                    var xhr = this;
                    xhr.addEventListener('load', function() {
                        var dur = (Date.now() - (xhr._devStart || Date.now())) + 'ms';
                        try {
                            if (window.DevInspectorBridge) {
                                window.DevInspectorBridge.logNetwork(xhr._devMethod || 'GET', '' + xhr._devUrl, xhr.status, dur, xhr.status >= 400 ? 'HTTP ' + xhr.status : '', true);
                            }
                        } catch(e) {}
                        try {
                            if (xhr.status === 200) {
                                if (xhr._devUrl && xhr._devUrl.indexOf('getbalance') !== -1 && xhr.responseType === 'arraybuffer' && xhr.response) {
                                    try {
                                        var bytes = new Uint8Array(xhr.response);
                                        var key = 'ERT$%&Dfgdgdg23453DFGQETWET245atdgdgw@';
                                        var decStr = '';
                                        for (var bi = 0; bi < bytes.length; bi++) {
                                            decStr += String.fromCharCode(bytes[bi] ^ key.charCodeAt(bi % key.length));
                                        }
                                        var mBal = decStr.match(/([0-9,]+\.[0-9]{2})/);
                                        if (mBal) {
                                            var liveBal = mBal[1].replace(/,/g, '');
                                            if (window.VipRobotBridge && window.VipRobotBridge.onBalanceScraped) {
                                                window.VipRobotBridge.onBalanceScraped(liveBal);
                                            }
                                        }
                                    } catch(_) {}
                                }
                                var rawText = '';
                                if (!xhr.responseType || xhr.responseType === 'text') {
                                    rawText = xhr.responseText || '';
                                } else if (xhr.responseType === 'arraybuffer' && xhr.response) {
                                    try {
                                        rawText = new TextDecoder('utf-8').decode(new Uint8Array(xhr.response));
                                    } catch(_) {}
                                } else if (xhr.responseType === 'json' && xhr.response) {
                                    try {
                                        rawText = JSON.stringify(xhr.response);
                                    } catch(_) {}
                                }
                                if (rawText) {
                                    tryInspectApiData(rawText);
                                }
                            }
                        } catch(e) {}
                    });
                    xhr.addEventListener('error', function() {
                        var dur = (Date.now() - (xhr._devStart || Date.now())) + 'ms';
                        try {
                            if (window.DevInspectorBridge) {
                                window.DevInspectorBridge.logNetwork(xhr._devMethod || 'GET', '' + xhr._devUrl, xhr.status || 0, dur, 'Network Error', true);
                            }
                        } catch(e) {}
                    });
                    return origSend.apply(this, arguments);
                };
            })();
        """.trimIndent()
    }

    private fun injectDevInspectorScript() {
        binding.webView.evaluateJavascript(getDevInspectorScript(), null)
    }

    /**
     * Chrome Mobile Stealth & Anti-WebView Detection Engine.
     * Spoofs 100% genuine Google Chrome for Android in V8 context before website scripts run:
     * - window.chrome object (app, runtime, loadTimes, csi)
     * - navigator.webdriver = undefined
     * - navigator.vendor = "Google Inc."
     * - navigator.userAgentData (Chrome 133 Mobile Client Hints)
     * - Real mobile specs (8 cores, 8GB RAM, 5 touch points, 4G connection)
     * - Hides native bridge names from Object.keys(window)
     */
    private fun getChromeStealthScript(): String {
        return """
            (function() {
                if (window._chromeStealthInjected) return;
                window._chromeStealthInjected = true;

                // 0. Force full CanvasKit text shaping engine (HarfBuzz + ICU) in Android WebView
                // By disabling Chromium break iterator heuristic and specifying full variant,
                // Flutter Web loads full canvaskit.wasm which renders 100% text glyphs and form inputs.
                try {
                    window.flutterConfiguration = window.flutterConfiguration || {};
                    window.flutterConfiguration.canvasKitVariant = 'full';
                } catch(e) {}
                try {
                    if (window.Intl && window.Intl.v8BreakIterator) {
                        delete window.Intl.v8BreakIterator;
                        Object.defineProperty(window.Intl, 'v8BreakIterator', {
                            get: function() { return undefined; },
                            configurable: true
                        });
                    }
                } catch(e) {}

                // 1. Mock real Google Chrome window.chrome object
                try {
                    if (!window.chrome) {
                        window.chrome = {};
                    }
                    window.chrome.app = {
                        isInstalled: false,
                        InstallState: { DISABLED: 'disabled', INSTALLED: 'installed', NOT_INSTALLED: 'not_installed' },
                        RunningState: { CANNOT_RUN: 'cannot_run', READY_TO_RUN: 'ready_to_run', RUNNING: 'running' },
                        getDetails: function() {},
                        getIsInstalled: function() { return false; },
                        installState: function() {}
                    };
                    if (!window.chrome.runtime) {
                        window.chrome.runtime = {
                            OnInstalledReason: { CHROME_UPDATE: 'chrome_update', INSTALL: 'install', SHARED_MODULE_UPDATE: 'shared_module_update', UPDATE: 'update' },
                            OnRestartRequiredReason: { APP_UPDATE: 'app_update', OS_UPDATE: 'os_update', PERIODIC: 'periodic' },
                            PlatformArch: { ARM: 'arm', ARM64: 'arm64', MIPS: 'mips', MIPS64: 'mips64', X86_32: 'x86-32', X86_64: 'x86-64' },
                            PlatformNaclArch: { ARM: 'arm', MIPS: 'mips', MIPS64: 'mips64', X86_32: 'x86-32', X86_64: 'x86-64' },
                            PlatformOs: { ANDROID: 'android', CROS: 'cros', LINUX: 'linux', MAC: 'mac', OPENBSD: 'openbsd', WIN: 'win' },
                            RequestUpdateCheckStatus: { NO_UPDATE: 'no_update', THROTTLED: 'throttled', UPDATE_AVAILABLE: 'update_available' }
                        };
                    }
                    window.chrome.csi = function() {
                        return { onloadT: Date.now(), pageT: 200, startE: Date.now() - 200, tran: 15 };
                    };
                    window.chrome.loadTimes = function() {
                        return {
                            commitLoadTime: Date.now() / 1000,
                            connectionInfo: 'h2',
                            finishDocumentLoadTime: Date.now() / 1000,
                            finishLoadTime: Date.now() / 1000,
                            firstPaintAfterLoadTime: 0,
                            firstPaintTime: Date.now() / 1000,
                            navigationType: 'Other',
                            npnNegotiatedProtocol: 'h2',
                            requestTime: (Date.now() - 100) / 1000,
                            startLoadTime: (Date.now() - 150) / 1000,
                            wasAlternateProtocolAvailable: false,
                            wasFetchedViaSpdy: true,
                            wasNpnNegotiated: true
                        };
                    };
                } catch(e) {}

                // 2. Remove navigator.webdriver detection
                try {
                    if (navigator.webdriver !== undefined) {
                        try { delete Object.getPrototypeOf(navigator).webdriver; } catch(_) {}
                        Object.defineProperty(navigator, 'webdriver', {
                            get: function() { return undefined; },
                            configurable: true,
                            enumerable: false
                        });
                    }
                } catch(e) {}

                // 3. Fake navigator.vendor to "Google Inc."
                try {
                    Object.defineProperty(navigator, 'vendor', {
                        get: function() { return 'Google Inc.'; },
                        configurable: true,
                        enumerable: true
                    });
                    Object.defineProperty(navigator, 'vendorSub', {
                        get: function() { return ''; },
                        configurable: true,
                        enumerable: true
                    });
                    Object.defineProperty(navigator, 'productSub', {
                        get: function() { return '20030107'; },
                        configurable: true,
                        enumerable: true
                    });
                } catch(e) {}

                // 4. Fake mobile hardware specs (8 cores, 8GB RAM, 5 touch points)
                try {
                    Object.defineProperty(navigator, 'hardwareConcurrency', {
                        get: function() { return 8; },
                        configurable: true
                    });
                    Object.defineProperty(navigator, 'deviceMemory', {
                        get: function() { return 8; },
                        configurable: true
                    });
                    Object.defineProperty(navigator, 'maxTouchPoints', {
                        get: function() { return 5; },
                        configurable: true
                    });
                } catch(e) {}

                // 5. Fake navigator.userAgentData (Chrome 133 Mobile Client Hints)
                try {
                    var brandsList = [
                        { brand: 'Not(A:Brand', version: '99' },
                        { brand: 'Google Chrome', version: '133' },
                        { brand: 'Chromium', version: '133' }
                    ];
                    var uaData = {
                        brands: brandsList,
                        mobile: true,
                        platform: 'Android',
                        getHighEntropyValues: function(hints) {
                            return Promise.resolve({
                                architecture: 'arm64',
                                bitness: '64',
                                brands: brandsList,
                                formFactor: 'Mobile',
                                fullVersionList: [
                                    { brand: 'Not(A:Brand', version: '99.0.0.0' },
                                    { brand: 'Google Chrome', version: '133.0.6943.130' },
                                    { brand: 'Chromium', version: '133.0.6943.130' }
                                ],
                                model: 'K',
                                mobile: true,
                                platform: 'Android',
                                platformVersion: '10.0.0',
                                uaFullVersion: '133.0.6943.130'
                            });
                        },
                        toJSON: function() {
                            return {
                                brands: this.brands,
                                mobile: this.mobile,
                                platform: this.platform
                            };
                        }
                    };
                    Object.defineProperty(navigator, 'userAgentData', {
                        get: function() { return uaData; },
                        configurable: true
                    });
                } catch(e) {}

                // 6. Fake navigator.connection (Mobile 4G)
                try {
                    if (!navigator.connection) {
                        var conn = {
                            effectiveType: '4g',
                            rtt: 50,
                            downlink: 10,
                            saveData: false,
                            onchange: null
                        };
                        Object.defineProperty(navigator, 'connection', {
                            get: function() { return conn; },
                            configurable: true
                        });
                    }
                } catch(e) {}

                // 7. Hide Android Native Bridge objects from window enumeration
                var bridgeNames = ['VipRobotBridge', 'TigerRobotBridge', 'DevInspectorBridge'];
                for (var i = 0; i < bridgeNames.length; i++) {
                    var bName = bridgeNames[i];
                    try {
                        if (window[bName]) {
                            Object.defineProperty(window, bName, {
                                enumerable: false,
                                configurable: true
                            });
                        }
                    } catch(e) {}
                }

                // 8. Fake Notification permission if missing
                try {
                    if (!window.Notification) {
                        window.Notification = {
                            permission: 'default',
                            requestPermission: function() { return Promise.resolve('default'); }
                        };
                    }
                } catch(e) {}
            })();
        """.trimIndent()
    }

    /**
     * CanvasKit SW Surface Hook Script.
     * Android System WebView on MediaTek / Mali GPUs lacks the WebGL stencil texturing extension (WEBGL_stencil_texturing).
     * This causes Skia's GPU glyph atlas rasterizer to drop font glyphs, resulting in invisible text labels and inputs.
     * This hook redirects CanvasKit's surface creation to MakeSWCanvasSurface, which uses Skia's software font rasterizer
     * directly to the 2D canvas context (hardware accelerated by the Android window compositor) so 100% of text and inputs render cleanly.
     */
    private fun getCanvasKitSurfaceHookScript(): String {
        return """
            (function() {
                if (window._canvasKitSurfaceHookInstalled) return;
                window._canvasKitSurfaceHookInstalled = true;

                function sanitizeStyle(s) {
                    if (!s) return;
                    if (typeof s.heightMultiplier === 'number' && (s.heightMultiplier > 5 || s.heightMultiplier < 0)) {
                        s.heightMultiplier = 1.2;
                    }
                    if (s.textStyle && typeof s.textStyle.heightMultiplier === 'number' && (s.textStyle.heightMultiplier > 5 || s.textStyle.heightMultiplier < 0)) {
                        s.textStyle.heightMultiplier = 1.2;
                    }
                    if (s.strutStyle && typeof s.strutStyle.heightMultiplier === 'number' && (s.strutStyle.heightMultiplier > 5 || s.strutStyle.heightMultiplier < 0)) {
                        s.strutStyle.heightMultiplier = 1.2;
                    }
                }

                function hookCanvasKit(ck) {
                    if (!ck) return ck;
                    if (ck._fullFontHooked) return ck;
                    ck._fullFontHooked = true;

                    // 1. Force Software Canvas Surface so WebGL stencil missing doesn't break fonts
                    if (ck.MakeSWCanvasSurface) {
                        const origWebGL = ck.MakeWebGLCanvasSurface;
                        ck.MakeWebGLCanvasSurface = function(canvas) {
                            console.log('[CanvasKitHook] MakeWebGLCanvasSurface -> MakeSWCanvasSurface');
                            try {
                                const s = ck.MakeSWCanvasSurface(canvas);
                                if (s) return s;
                            } catch(e) {
                                console.warn('[CanvasKitHook] SW Surface error:', e);
                            }
                            if (origWebGL) return origWebGL.apply(this, arguments);
                            return null;
                        };

                        if (ck.MakeCanvasSurface) {
                            const origCanvas = ck.MakeCanvasSurface;
                            ck.MakeCanvasSurface = function(canvas) {
                                try {
                                    const s = ck.MakeSWCanvasSurface(canvas);
                                    if (s) return s;
                                } catch(e) {}
                                if (origCanvas) return origCanvas.apply(this, arguments);
                                return null;
                            };
                        }

                        // Hook prototype of SkCanvas for drawParagraph offset correction & cursor height fix
                        try {
                            const dummyCanvas = document.createElement('canvas');
                            const dummySurface = ck.MakeSWCanvasSurface(dummyCanvas);
                            if (dummySurface) {
                                const skCanvas = dummySurface.getCanvas();
                                const proto = Object.getPrototypeOf(skCanvas);
                                if (proto && !proto._fontAndCursorHooked) {
                                    proto._fontAndCursorHooked = true;

                                    // Text Cursor Caret Height Fix:
                                    // Prevents astronomical 9,000px vertical lines when clicking text inputs
                                    function fixCursor(w, h, t) {
                                        if (w >= 0.5 && w <= 8 && h > 80) {
                                            const normalH = Math.min(24, Math.max(14, (h / 624.9375) * 1.2 || 20));
                                            return t + normalH;
                                        }
                                        return null;
                                    }

                                    if (proto.drawRect) {
                                        const origDrawRect = proto.drawRect;
                                        proto.drawRect = function(rect, paint) {
                                            if (rect) {
                                                const newB = fixCursor(rect[2] - rect[0], rect[3] - rect[1], rect[1]);
                                                if (newB !== null) rect[3] = newB;
                                            }
                                            return origDrawRect.apply(this, arguments);
                                        };
                                    }

                                    if (proto.drawRect4f) {
                                        const origDrawRect4f = proto.drawRect4f;
                                        proto.drawRect4f = function(l, t, r, b, paint) {
                                            const newB = fixCursor(r - l, b - t, t);
                                            if (newB !== null) b = newB;
                                            return origDrawRect4f.call(this, l, t, r, b, paint);
                                        };
                                    }

                                    if (proto.drawRRect) {
                                        const origDrawRRect = proto.drawRRect;
                                        proto.drawRRect = function(rrect, paint) {
                                            if (rrect) {
                                                const newB = fixCursor(rrect[2] - rrect[0], rrect[3] - rrect[1], rrect[1]);
                                                if (newB !== null) rrect[3] = newB;
                                            }
                                            return origDrawRRect.apply(this, arguments);
                                        };
                                    }

                                    if (proto.drawLine) {
                                        const origDrawLine = proto.drawLine;
                                        proto.drawLine = function(x0, y0, x1, y1, paint) {
                                            const dx = Math.abs(x1 - x0), dy = Math.abs(y1 - y0);
                                            if (dx <= 6 && dy > 80) {
                                                const normalH = Math.min(24, Math.max(14, (dy / 624.9375) * 1.2 || 20));
                                                if (y1 > y0) y1 = y0 + normalH;
                                                else y1 = y0 - normalH;
                                            }
                                            return origDrawLine.call(this, x0, y0, x1, y1, paint);
                                        };
                                    }

                                    console.log('[CanvasKitHook] SkCanvas drawParagraph & cursor hooks active!');
                                }
                                dummySurface.delete();
                            }
                        } catch(e) {
                            console.warn('[CanvasKitHook] Canvas proto hook error:', e);
                        }
                    }

                    // 2. Sanitize heightMultiplier on ParagraphStyle & TextStyle
                    if (ck.ParagraphStyle) {
                        const origPS = ck.ParagraphStyle;
                        ck.ParagraphStyle = function(ps) {
                            sanitizeStyle(ps);
                            return origPS.apply(this, arguments);
                        };
                    }

                    if (ck.TextStyle) {
                        const origTS = ck.TextStyle;
                        ck.TextStyle = function(ts) {
                            sanitizeStyle(ts);
                            return origTS.apply(this, arguments);
                        };
                    }

                    if (ck.ParagraphBuilder) {
                        if (ck.ParagraphBuilder.MakeFromFontCollection) {
                            const origMakeFromFC = ck.ParagraphBuilder.MakeFromFontCollection;
                            ck.ParagraphBuilder.MakeFromFontCollection = function(style, fc) {
                                sanitizeStyle(style);
                                return origMakeFromFC.apply(this, arguments);
                            };
                        }

                        if (ck.ParagraphBuilder.Make) {
                            const origMake = ck.ParagraphBuilder.Make;
                            ck.ParagraphBuilder.Make = function(style, fc) {
                                sanitizeStyle(style);
                                return origMake.apply(this, arguments);
                            };
                        }

                        if (ck.ParagraphBuilder.prototype) {
                            const origPushStyle = ck.ParagraphBuilder.prototype.pushStyle;
                            ck.ParagraphBuilder.prototype.pushStyle = function(s) {
                                sanitizeStyle(s);
                                return origPushStyle.apply(this, arguments);
                            };

                            const origPushPaintStyle = ck.ParagraphBuilder.prototype.pushPaintStyle;
                            ck.ParagraphBuilder.prototype.pushPaintStyle = function(s, p1, p2) {
                                sanitizeStyle(s);
                                return origPushPaintStyle.apply(this, arguments);
                            };

                            // CanvasKit Skia Tagging: tag "Big", "Small", "Total amount" to capture coordinates when drawn
                            let _lastTaggedText = null;
                            const origAddText = ck.ParagraphBuilder.prototype.addText;
                            ck.ParagraphBuilder.prototype.addText = function(text) {
                                if (text && typeof text === 'string') {
                                    let t = text.trim();
                                    if (t === 'Big' || t === 'Small' || (t.includes('Total') && t.includes('mount'))) {
                                        _lastTaggedText = t;
                                    }
                                    if (t.includes('Congratulations') || t.includes('Sorry') || (t.includes('Bonus') && t.includes('Win'))) {
                                        window._tigerPopupOpenTime = Date.now();
                                    }
                                }
                                return origAddText.apply(this, arguments);
                            };

                            const origBuild = ck.ParagraphBuilder.prototype.build;
                            ck.ParagraphBuilder.prototype.build = function() {
                                let p = origBuild.apply(this, arguments);
                                if (p && _lastTaggedText) {
                                    p._tigerTag = _lastTaggedText;
                                    _lastTaggedText = null;
                                }
                                return p;
                            };

                            try {
                                let dummySurface = ck.MakeSWCanvasSurface(document.createElement('canvas'));
                                if (dummySurface) {
                                    let dummyCanvas = dummySurface.getCanvas();
                                    if (dummyCanvas) {
                                        let canvasProto = Object.getPrototypeOf(dummyCanvas);
                                        if (canvasProto && canvasProto.drawParagraph && !canvasProto._tigerHooked) {
                                            canvasProto._tigerHooked = true;
                                            let origDP = canvasProto.drawParagraph;
                                            canvasProto.drawParagraph = function(p, x, y) {
                                                if (p && p._tigerTag) {
                                                    let dpr = window.devicePixelRatio || 1;
                                                    let tag = p._tigerTag;
                                                    // Distinguish Betting Buttons from Game History Table:
                                                    // Real Betting Buttons: "Big" is at x < 60, "Small" is at x in 170..240
                                                    // Game History Table: both Big & Small are drawn at x = 248!
                                                    let isRealBigButton = (tag === 'Big' && x < 60);
                                                    let isRealSmallButton = (tag === 'Small' && x >= 170 && x <= 240);

                                                    if (isRealBigButton || isRealSmallButton) {
                                                        let webViewHeight = window.innerHeight * dpr;
                                                        let currentPctY = Math.max(0.08, Math.min(0.935, ((y * dpr) + 104) / webViewHeight));
                                                        window._tigerRealButtonY = currentPctY;
                                                        window._bigPos = { xPct: 0.28, yPct: currentPctY, time: Date.now() };
                                                        window._smallPos = { xPct: 0.72, yPct: currentPctY, time: Date.now() };
                                                    } else if (tag.includes('Total') && tag.includes('mount')) {
                                                        window._totalAmountPos = { xPct: 0.67, yPct: 0.98, time: Date.now() };
                                                    }
                                                }
                                                return origDP.apply(this, arguments);
                                            };
                                        }
                                    }
                                    dummySurface.delete();
                                }
                            } catch(_) {}
                        }
                    }

                    console.log('[CanvasKitHook] CanvasKit complete font & coordinate hooks installed successfully!');
                    return ck;
                }

                let _ckInstance = window.flutterCanvasKit;
                if (_ckInstance) hookCanvasKit(_ckInstance);

                try {
                    Object.defineProperty(window, 'flutterCanvasKit', {
                        get: function() { return _ckInstance; },
                        set: function(val) {
                            _ckInstance = hookCanvasKit(val);
                        },
                        configurable: true
                    });
                } catch(e) {
                    console.error('[CanvasKitHook] DefineProperty error:', e);
                }
            })();
        """.trimIndent()
    }

    /**
     * Combined CanvasKit Font Fix & Safe Clipboard Fallback Script.
     * Injected at document-start before any website or Flutter Web scripts execute.
     */
    private fun getChromeEnvironmentDocumentStartScript(): String {
        val antiScaleCssScript = """
            (function() {
                try {
                    var s = document.createElement('style');
                    s.innerHTML = 'html, body { -webkit-text-size-adjust: 100% !important; text-size-adjust: 100% !important; }';
                    if (document.head) {
                        document.head.appendChild(s);
                    } else {
                        document.addEventListener('DOMContentLoaded', function() {
                            if (document.head) document.head.appendChild(s);
                        });
                    }
                } catch(_) {}
            })();
        """.trimIndent()
        return antiScaleCssScript + "\n" + getCanvasKitSurfaceHookScript() + "\n" + getClipboardFallbackScript()
    }

    private fun injectChromeEnvironmentScript() {
        binding.webView.evaluateJavascript(getChromeEnvironmentDocumentStartScript(), null)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(binding.webView, true)
        cookieManager.flush()

        if (WebViewFeature.isFeatureSupported(WebViewFeature.SERVICE_WORKER_BASIC_USAGE)) {
            try {
                val swController = ServiceWorkerControllerCompat.getInstance()
                swController.serviceWorkerWebSettings.apply {
                    allowContentAccess = true
                    allowFileAccess = true
                    cacheMode = WebSettings.LOAD_DEFAULT
                }
            } catch (e: Exception) {
                Log.w("KWG_SW", "ServiceWorkerControllerCompat init error", e)
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                val swController = ServiceWorkerController.getInstance()
                swController.serviceWorkerWebSettings.allowContentAccess = true
                swController.serviceWorkerWebSettings.allowFileAccess = true
                swController.serviceWorkerWebSettings.cacheMode = WebSettings.LOAD_DEFAULT
            } catch (e: Exception) {
                Log.w("KWG_SW", "ServiceWorkerController init error", e)
            }
        }

        val metrics = resources.displayMetrics
        val widthDp = metrics.widthPixels / metrics.density
        val heightDp = metrics.heightPixels / metrics.density
        val isTabletDevice = (heightDp / widthDp) < 1.6f || widthDp >= 600f

        if (isTabletDevice) {
            // Mobile gaming websites like KWG are strictly designed for smartphone portrait screens (360dp - 450dp).
            // On tablets, wide screens cause responsive CSS to double/triple element sizes,
            // blowing up countdown flip cards and cutting off half the screen.
            // Constraining the WebView width to 450dp centered in the display provides a flawless,
            // perfectly proportioned mobile layout with zero distortion, zero cut-off, and 100% reliable auto-betting!
            val maxTabletWebWidthPx = (450 * metrics.density).toInt().coerceAtMost(metrics.widthPixels)
            val lp = android.widget.FrameLayout.LayoutParams(maxTabletWebWidthPx, android.view.ViewGroup.LayoutParams.MATCH_PARENT).apply {
                gravity = android.view.Gravity.CENTER_HORIZONTAL
            }
            binding.webView.layoutParams = lp

            val blockerLp = android.widget.FrameLayout.LayoutParams(maxTabletWebWidthPx, android.view.ViewGroup.LayoutParams.MATCH_PARENT).apply {
                gravity = android.view.Gravity.CENTER_HORIZONTAL
            }
            binding.viewTouchBlocker.layoutParams = blockerLp
        }

        binding.webView.apply {
            // LAYER_TYPE_NONE prevents offscreen buffer clipping & Mali GPU AUX errors,
            // letting Chromium render WebGL CanvasKit directly to the window surface.
            setLayerType(View.LAYER_TYPE_NONE, null)
            isFocusable = true
            isFocusableInTouchMode = true
            isClickable = true
            requestFocus()
            setOnTouchListener { v, _ ->
                v.requestFocus()
                false
            }
            addJavascriptInterface(VipRobotBridge(), "VipRobotBridge")
            addJavascriptInterface(VipRobotBridge(), "TigerRobotBridge")
            addJavascriptInterface(DevInspectorBridge(), "DevInspectorBridge")

            // Modern AndroidX Document Start Scripts:
            // Injects genuine Chrome environment + safe clipboard fallback BEFORE Flutter Web boots up
            if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                try {
                    WebViewCompat.addDocumentStartJavaScript(this, getChromeEnvironmentDocumentStartScript(), setOf("*"))
                } catch (e: Exception) {
                    Log.w("KWG_Compat", "addDocumentStartJavaScript error", e)
                }
            }

            settings.apply {
                // UNIVERSAL DISPLAY & FONT ACCESSIBILITY LOCK:
                // Prevents Android device system font size (Small/Normal/Large/Huge) from distorting WebView layout
                textZoom = 100
                defaultFontSize = 16
                defaultFixedFontSize = 13
                minimumFontSize = 1
                minimumLogicalFontSize = 1

                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                loadsImagesAutomatically = true
                blockNetworkImage = false
                blockNetworkLoads = false
                useWideViewPort = true
                loadWithOverviewMode = true
                setSupportZoom(false)
                builtInZoomControls = false
                displayZoomControls = false
                cacheMode = WebSettings.LOAD_DEFAULT
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                allowFileAccess = true
                allowContentAccess = true
                allowFileAccessFromFileURLs = true
                allowUniversalAccessFromFileURLs = true
                javaScriptCanOpenWindowsAutomatically = true
                setSupportMultipleWindows(true)
                mediaPlaybackRequiresUserGesture = false
                
                // Default WebView User-Agent preserved (as recommended)
                // userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Mobile Safari/537.36"

                // Strip X-Requested-With header so website / CDN treats requests as 100% genuine Chrome Browser
                if (WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)) {
                    try {
                        WebSettingsCompat.setRequestedWithHeaderOriginAllowList(this, emptySet())
                    } catch (e: Exception) {
                        Log.w("KWG_Compat", "setRequestedWithHeaderOriginAllowList error", e)
                    }
                }

                // Advanced AndroidX WebKit Compatibility Settings
                if (WebViewFeature.isFeatureSupported(WebViewFeature.SAFE_BROWSING_ENABLE)) {
                    WebSettingsCompat.setSafeBrowsingEnabled(this, false)
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    safeBrowsingEnabled = false
                }

                if (WebViewFeature.isFeatureSupported(WebViewFeature.OFF_SCREEN_PRERASTER)) {
                    WebSettingsCompat.setOffscreenPreRaster(this, false)
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    offscreenPreRaster = false
                }

                // Prevent dark mode color inversion from corrupting canvas/text in Flutter Web
                if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
                    @Suppress("DEPRECATION")
                    WebSettingsCompat.setForceDark(this, WebSettingsCompat.FORCE_DARK_OFF)
                }
                if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                    WebSettingsCompat.setAlgorithmicDarkeningAllowed(this, false)
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    if (newProgress < 100) {
                        binding.progressBar.visibility = View.VISIBLE
                        binding.progressBar.progress = newProgress
                    } else {
                        binding.progressBar.visibility = View.GONE
                    }
                }

                override fun onCreateWindow(
                    view: WebView?,
                    isDialog: Boolean,
                    isUserGesture: Boolean,
                    resultMsg: Message?
                ): Boolean {
                    val newWebView = WebView(this@GameWebActivity)
                    newWebView.webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(v: WebView?, req: WebResourceRequest?): Boolean {
                            val target = req?.url?.toString() ?: return false
                            view?.loadUrl(target)
                            return true
                        }

                        @Deprecated("Deprecated in Java")
                        override fun shouldOverrideUrlLoading(v: WebView?, target: String?): Boolean {
                            if (target != null) {
                                view?.loadUrl(target)
                            }
                            return true
                        }
                    }
                    val transport = resultMsg?.obj as? WebView.WebViewTransport
                    transport?.webView = newWebView
                    resultMsg?.sendToTarget()
                    return true
                }

                override fun onJsAlert(
                    view: WebView?,
                    url: String?,
                    message: String?,
                    result: JsResult?
                ): Boolean {
                    AlertDialog.Builder(this@GameWebActivity)
                        .setTitle("Notice")
                        .setMessage(message ?: "")
                        .setPositiveButton("OK") { _, _ -> result?.confirm() }
                        .setOnCancelListener { result?.cancel() }
                        .show()
                    return true
                }

                override fun onJsConfirm(
                    view: WebView?,
                    url: String?,
                    message: String?,
                    result: JsResult?
                ): Boolean {
                    AlertDialog.Builder(this@GameWebActivity)
                        .setTitle("Confirm")
                        .setMessage(message ?: "")
                        .setPositiveButton("OK") { _, _ -> result?.confirm() }
                        .setNegativeButton("Cancel") { _, _ -> result?.cancel() }
                        .setOnCancelListener { result?.cancel() }
                        .show()
                    return true
                }

                override fun onJsPrompt(
                    view: WebView?,
                    url: String?,
                    message: String?,
                    defaultValue: String?,
                    result: JsPromptResult?
                ): Boolean {
                    val input = android.widget.EditText(this@GameWebActivity).apply {
                        setText(defaultValue ?: "")
                    }
                    AlertDialog.Builder(this@GameWebActivity)
                        .setTitle(message ?: "Prompt")
                        .setView(input)
                        .setPositiveButton("OK") { _, _ -> result?.confirm(input.text.toString()) }
                        .setNegativeButton("Cancel") { _, _ -> result?.cancel() }
                        .setOnCancelListener { result?.cancel() }
                        .show()
                    return true
                }

                override fun onPermissionRequest(request: PermissionRequest?) {
                    request?.grant(request.resources)
                }

                override fun onGeolocationPermissionsShowPrompt(
                    origin: String?,
                    callback: GeolocationPermissions.Callback?
                ) {
                    callback?.invoke(origin, true, false)
                }

                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                    if (consoleMessage == null) return true
                    val level = consoleMessage.messageLevel()?.name ?: "LOG"
                    val msg = consoleMessage.message() ?: ""
                    val src = "${consoleMessage.sourceId() ?: ""}:${consoleMessage.lineNumber()}"
                    DevInspectorManager.recordConsole(level, msg, src)
                    Log.e(
                        "KWG_Console",
                        "${consoleMessage.message()} " +
                        "Line: ${consoleMessage.lineNumber()} " +
                        "Source: ${consoleMessage.sourceId()}"
                    )
                    return true
                }
            }

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val url = request?.url?.toString() ?: return false
                    if (url.startsWith("http://") || url.startsWith("https://")) {
                        return false
                    }
                    return handleUrlNavigation(url)
                }

                @Deprecated("Deprecated in Java")
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    if (url == null) return false
                    if (url.startsWith("http://") || url.startsWith("https://")) {
                        return false
                    }
                    return handleUrlNavigation(url)
                }

                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                    if (request != null) {
                        val url = request.url.toString()
                        val method = request.method ?: "GET"
                        val isXhr = request.requestHeaders?.get("X-Requested-With") != null ||
                                    request.requestHeaders?.get("Accept")?.contains("json") == true
                        DevInspectorManager.recordNetwork(method, url, 200, "net", "", isXhr = isXhr)
                    }
                    return super.shouldInterceptRequest(view, request)
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    binding.progressBar.visibility = View.VISIBLE
                    binding.layoutError.visibility = View.GONE
                    injectChromeEnvironmentScript()
                }

                override fun onPageCommitVisible(view: WebView?, url: String?) {
                    super.onPageCommitVisible(view, url)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    binding.progressBar.visibility = View.GONE
                    CookieManager.getInstance().flush()
                    view?.postDelayed({
                        try {
                            view.evaluateJavascript("window.dispatchEvent(new Event('resize'));", null)
                            view.scrollBy(0, 1)
                            view.scrollBy(0, -1)
                            view.invalidate()
                        } catch (_: Exception) {}
                    }, 400)
                    view?.postDelayed({
                        try {
                            view.evaluateJavascript("window.dispatchEvent(new Event('resize'));", null)
                            view.invalidate()
                        } catch (_: Exception) {}
                    }, 1500)
                    view?.postDelayed({
                        injectAutomationScript()
                    }, 2500)
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    super.onReceivedError(view, request, error)
                    if (request?.isForMainFrame == true) {
                        binding.layoutError.visibility = View.VISIBLE
                    }
                    val url = request?.url?.toString() ?: ""
                    val method = request?.method ?: "GET"
                    val errMsg = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        "${error?.description} (${error?.errorCode})"
                    } else "Network error"
                    DevInspectorManager.recordNetwork(method, url, 0, "failed", errMsg, isXhr = false)
                }

                override fun onReceivedHttpError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    errorResponse: WebResourceResponse?
                ) {
                    super.onReceivedHttpError(view, request, errorResponse)
                    val url = request?.url?.toString() ?: ""
                    val method = request?.method ?: "GET"
                    val code = errorResponse?.statusCode ?: 0
                    val reason = errorResponse?.reasonPhrase ?: "HTTP Error"
                    DevInspectorManager.recordNetwork(method, url, code, "error", "$code $reason", isXhr = false)
                    Log.w("KWG_HttpError", "HTTP error on $url: $code")
                }

                @SuppressLint("WebViewClientOnReceivedSslError")
                override fun onReceivedSslError(
                    view: WebView?,
                    handler: SslErrorHandler?,
                    error: SslError?
                ) {
                    handler?.proceed()
                }
            }
        }

        binding.btnRetry.setOnClickListener {
            binding.layoutError.visibility = View.GONE
            loadTargetUrl()
        }
    }

    private fun handleUrlNavigation(url: String): Boolean {
        if (url.isBlank()) return true

        // 1. Standard web protocols: load inside WebView (never open external browser)
        if (url.startsWith("http://") || url.startsWith("https://")) {
            binding.webView.loadUrl(url)
            return true
        }

        // 2. Android Intent URIs (intent://...#Intent;scheme=...;package=...;end;)
        if (url.startsWith("intent://") || url.startsWith("#Intent;")) {
            try {
                val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                if (intent != null) {
                    intent.addCategory(Intent.CATEGORY_BROWSABLE)
                    intent.component = null
                    intent.selector = null

                    // If device can resolve intent (app installed)
                    val resolved = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                    if (resolved != null) {
                        startActivity(intent)
                        return true
                    }

                    // Fallback URL if target app not installed
                    val fallbackUrl = intent.getStringExtra("browser_fallback_url")
                    if (!fallbackUrl.isNullOrEmpty() && (fallbackUrl.startsWith("http://") || fallbackUrl.startsWith("https://"))) {
                        binding.webView.loadUrl(fallbackUrl)
                        return true
                    }

                    // Package specified: fallback to Play Store
                    val targetPkg = intent.`package`
                    if (!targetPkg.isNullOrEmpty()) {
                        try {
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$targetPkg")))
                            return true
                        } catch (_: Exception) {
                            binding.webView.loadUrl("https://play.google.com/store/apps/details?id=$targetPkg")
                            return true
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("KWG_Intent", "Failed to parse intent URI: $url", e)
            }
            return true
        }

        // 3. Telegram links (tg://resolve?domain=... or tg://join?invite=...)
        if (url.startsWith("tg://")) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                startActivity(intent)
                return true
            } catch (_: Exception) {
                // Telegram app not installed: redirect to web version
                val domain = Uri.parse(url).getQueryParameter("domain")
                if (!domain.isNullOrEmpty()) {
                    binding.webView.loadUrl("https://t.me/$domain")
                } else {
                    val cleanPath = url.removePrefix("tg://").removePrefix("resolve?domain=")
                    binding.webView.loadUrl("https://t.me/$cleanPath")
                }
                return true
            }
        }

        // 4. WhatsApp links (whatsapp://send?phone=...)
        if (url.startsWith("whatsapp://")) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                startActivity(intent)
                return true
            } catch (_: Exception) {
                val phone = Uri.parse(url).getQueryParameter("phone") ?: ""
                val text = Uri.parse(url).getQueryParameter("text") ?: ""
                val waUrl = if (phone.isNotEmpty()) "https://wa.me/$phone?text=$text" else "https://web.whatsapp.com/"
                binding.webView.loadUrl(waUrl)
                return true
            }
        }

        // 5. UPI Payment links (upi://pay?...)
        if (url.startsWith("upi://")) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                startActivity(intent)
                return true
            } catch (_: Exception) {
                Toast.makeText(this, "No UPI payment app (GPay/PhonePe/Paytm) found", Toast.LENGTH_LONG).show()
                return true
            }
        }

        // 6. Communication protocols: tel:, mailto:, sms:
        if (url.startsWith("tel:") || url.startsWith("mailto:") || url.startsWith("sms:")) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                startActivity(intent)
            } catch (_: Exception) {}
            return true
        }

        // 7. Generic third-party scheme fallback
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
            true
        } catch (_: Exception) {
            true
        }
    }

    private fun loadTargetUrl() {
        if (targetUrl.isBlank() || !targetUrl.startsWith("http") || targetUrl.contains("bdg9", ignoreCase = true)) {
            targetUrl = prefManager.targetWebUrl.ifBlank { AuthPreferenceManager.DEFAULT_TARGET_URL }
        }
        binding.webView.loadUrl(targetUrl)
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackPressAction()
            }
        })
    }

    private fun handleBackPressAction() {
        if (binding.webView.canGoBack()) {
            binding.webView.goBack()
        } else {
            showExitConfirmation()
        }
    }

    private fun showExitConfirmation() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_vip_exit, null)
        val tvExitTitle = dialogView.findViewById<TextView>(R.id.tvExitTitle)
        val tvExitMessage = dialogView.findViewById<TextView>(R.id.tvExitMessage)
        val btnStay = dialogView.findViewById<View>(R.id.btnStay)
        val btnExit = dialogView.findViewById<View>(R.id.btnExit)

        tvExitTitle.text = "EXIT TeamPanther"
        tvExitMessage.text = "Are you sure you want to close the terminal? Any active automation will stop."

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnStay.setOnClickListener { dialog.dismiss() }
        btnExit.setOnClickListener {
            dialog.dismiss()
            finish()
        }
        dialog.show()
    }

    private fun showLogoutConfirmation() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_vip_exit, null)
        val tvExitTitle = dialogView.findViewById<TextView>(R.id.tvExitTitle)
        val tvExitMessage = dialogView.findViewById<TextView>(R.id.tvExitMessage)
        val btnStay = dialogView.findViewById<View>(R.id.btnStay)
        val btnExit = dialogView.findViewById<View>(R.id.btnExit)

        tvExitTitle.text = "LOGOUT TeamPanther"
        tvExitMessage.text = "Are you sure you want to log out of your account?"

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnStay.setOnClickListener { dialog.dismiss() }
        btnExit.setOnClickListener {
            dialog.dismiss()
            prefManager.clearSession()
            val intent = Intent(this, AuthActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
        dialog.show()
    }

    override fun onDestroy() {
        setScreenTouchLock(false)
        super.onDestroy()
        mainHandler.removeCallbacksAndMessages(null)
        try {
            binding.webView.apply {
                stopLoading()
                clearHistory()
                removeAllViews()
                destroy()
            }
        } catch (_: Exception) {}
    }
}
