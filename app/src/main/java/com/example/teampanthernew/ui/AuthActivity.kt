package com.example.teampanthernew.ui

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Patterns
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.teampanthernew.R
import com.example.teampanthernew.data.AuthPreferenceManager
import com.example.teampanthernew.databinding.ActivityAuthBinding
import com.example.teampanthernew.utils.Tilt3DHelper
import com.google.android.material.snackbar.Snackbar
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLException
import kotlin.concurrent.thread

class AuthActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAuthBinding
    private lateinit var prefManager: AuthPreferenceManager

    private var isCurrentTabLogin = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        prefManager = AuthPreferenceManager(this)

        // If user is already logged in, navigate directly to Game Web Screen
        if (prefManager.isLoggedIn) {
            navigateToGameScreen()
            return
        }

        binding = ActivityAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)

        startEntranceAnimations()
        initSavedCredentials()
        setupTabSwitchers()
        setupInputWatchers()
        setupActionListeners()
    }

    /**
     * Starts rich visual animations & 3D Next.js-style effects on launch.
     */
    private fun startEntranceAnimations() {
        // Continuous 3D Floating / Levitation on the 3D logo
        Tilt3DHelper.start3DFloating(binding.layoutLogoContainer, floatDistanceDp = 8f, tiltDegrees = 5f, durationMs = 2800)

        // Slide up + Fade in entrance animation for auth card
        val slideUpAnim = AnimationUtils.loadAnimation(this, R.anim.anim_fade_slide_up)
        binding.cardAuthContainer.startAnimation(slideUpAnim)

        // Interactive 3D Parallax Tilt on Main Auth Card with Specular Light Glare
        Tilt3DHelper.attach3DTilt(
            targetView = binding.cardAuthContainer,
            maxTiltDegrees = 7f,
            scaleOnTouch = 1.01f,
            glareView = binding.viewCardGlare
        )

        // Interactive 3D Tilt on Logo
        Tilt3DHelper.attach3DTilt(
            targetView = binding.layoutLogoContainer,
            maxTiltDegrees = 18f,
            scaleOnTouch = 1.08f
        )

        // 3D Tactile Button Press Physics (Push depth & spring rebound)
        Tilt3DHelper.attach3DPressEffect(binding.btnLogin, pressedScale = 0.96f, depthDip = 6f)
        Tilt3DHelper.attach3DPressEffect(binding.btnRegister, pressedScale = 0.96f, depthDip = 6f)
    }

    /**
     * Pre-fills credentials if Remember Login was previously checked.
     */
    private fun initSavedCredentials() {
        if (prefManager.isRememberMe) {
            binding.cbRememberMe.isChecked = true
            binding.etLoginIdentifier.setText(prefManager.savedIdentifier)
            binding.etLoginPassword.setText(prefManager.savedPassword)
        }
    }

    /**
     * Configures the sleek segmented tab switcher between Login and Register.
     */
    private fun setupTabSwitchers() {
        binding.tabSignIn.setOnClickListener {
            if (!isCurrentTabLogin) {
                switchToTab(isLogin = true)
            }
        }

        binding.tabRegister.setOnClickListener {
            if (isCurrentTabLogin) {
                switchToTab(isLogin = false)
            }
        }

        binding.tvSwitchToRegister.setOnClickListener {
            switchToTab(isLogin = false)
        }

        binding.tvSwitchToLogin.setOnClickListener {
            switchToTab(isLogin = true)
        }
    }

    /**
     * Smoothly transitions between Login and Register views.
     */
    private fun switchToTab(isLogin: Boolean) {
        if (isCurrentTabLogin == isLogin) return
        isCurrentTabLogin = isLogin

        if (isLogin) {
            binding.tabSignIn.setBackgroundResource(R.drawable.bg_tab_active_gold)
            binding.tabSignIn.setTextColor(ContextCompat.getColor(this, R.color.vip_white))
            binding.tabRegister.background = null
            binding.tabRegister.setTextColor(ContextCompat.getColor(this, R.color.vip_text_secondary))

            binding.layoutRegister.visibility = View.GONE
            binding.layoutLogin.visibility = View.VISIBLE
        } else {
            binding.tabRegister.setBackgroundResource(R.drawable.bg_tab_active_gold)
            binding.tabRegister.setTextColor(ContextCompat.getColor(this, R.color.vip_white))
            binding.tabSignIn.background = null
            binding.tabSignIn.setTextColor(ContextCompat.getColor(this, R.color.vip_text_secondary))

            binding.layoutLogin.visibility = View.GONE
            binding.layoutRegister.visibility = View.VISIBLE
        }
    }

    /**
     * Clears error highlights as soon as the user starts correcting their input.
     */
    private fun setupInputWatchers() {
        binding.etLoginIdentifier.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.tilLoginIdentifier.error = null
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.etLoginPassword.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.tilLoginPassword.error = null
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.etRegPhone.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.tilRegPhone.error = null
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.etRegEmail.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.tilRegEmail.error = null
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.etRegPassword.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.tilRegPassword.error = null
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.etRegConfirmPassword.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.tilRegConfirmPassword.error = null
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    /**
     * Attaches button click handlers for Sign In, Registration and Forgot Password.
     */
    private fun setupActionListeners() {
        // Forgot Password Modal
        binding.tvForgotPassword.setOnClickListener {
            ForgotPasswordBottomSheet.newInstance().show(
                supportFragmentManager,
                ForgotPasswordBottomSheet.TAG
            )
        }

        // Login Button
        binding.btnLogin.setOnClickListener {
            attemptLogin()
        }

        // Register Button
        binding.btnRegister.setOnClickListener {
            attemptRegister()
        }
    }

    /**
     * Validates and executes user login via PHP API (https://team-panther.l.cd/api/login.php).
     */
    private fun attemptLogin() {
        val identifier = binding.etLoginIdentifier.text?.toString()?.trim() ?: ""
        val password = binding.etLoginPassword.text?.toString()?.trim() ?: ""

        var isValid = true

        if (identifier.isEmpty()) {
            binding.tilLoginIdentifier.error = "Please enter your email or phone number"
            isValid = false
        }

        if (password.isEmpty()) {
            binding.tilLoginPassword.error = getString(R.string.error_empty_password)
            isValid = false
        } else if (password.length < 6) {
            binding.tilLoginPassword.error = getString(R.string.error_short_password)
            isValid = false
        }

        if (!isValid) return

        // Save Remember Me preference
        val remember = binding.cbRememberMe.isChecked
        prefManager.saveLoginCredentials(identifier, password, remember)

        // Show loading state
        setLoginLoading(true)

        thread {
            var isSuccess = false
            var message = ""
            var uStatus = "active"
            var uId = 0
            var isVip = false
            var appStatus = "active"
            var uName = "VIP User"
            var uEmail = identifier
            var uPhone = identifier

            try {
                var loginUrlStr = prefManager.apiBaseUrl + "login.php"
                val payload = JSONObject().apply {
                    put("identifier", identifier)
                    put("password", password)
                }

                fun openConn(u: String): HttpURLConnection {
                    val conn = URL(u).openConnection() as HttpURLConnection
                    if (conn is HttpsURLConnection) {
                        conn.hostnameVerifier = HostnameVerifier { hostname, session ->
                            hostname == "panther-team.xyz.ci" || HttpsURLConnection.getDefaultHostnameVerifier().verify(hostname, session)
                        }
                    }
                    conn.requestMethod = "POST"
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.setRequestProperty("Accept", "application/json")
                    conn.connectTimeout = 7000
                    conn.readTimeout = 7000
                    conn.doOutput = true
                    OutputStreamWriter(conn.outputStream).use { it.write(payload.toString()) }
                    return conn
                }

                var conn: HttpURLConnection
                try {
                    conn = openConn(loginUrlStr)
                    conn.responseCode
                } catch (sslEx: SSLException) {
                    if (loginUrlStr.startsWith("https://")) {
                        loginUrlStr = loginUrlStr.replace("https://", "http://")
                        conn = openConn(loginUrlStr)
                    } else {
                        throw sslEx
                    }
                }

                val responseCode = conn.responseCode
                val stream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
                val responseText = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                val json = try { JSONObject(responseText) } catch (_: Exception) { null }

                if (json != null && json.optBoolean("success", false)) {
                    isSuccess = true
                    val userObj = json.optJSONObject("data")?.optJSONObject("user")
                    uId = userObj?.optInt("id") ?: 0
                    uName = userObj?.optString("username") ?: "VIP User"
                    uEmail = userObj?.optString("email") ?: identifier
                    uPhone = userObj?.optString("phone") ?: identifier
                    uStatus = userObj?.optString("status") ?: "active"
                    isVip = userObj?.optBoolean("is_vip") ?: false
                    appStatus = json.optJSONObject("data")?.optString("app_status") ?: "active"
                    message = json.optString("message", "Login successful")
                } else {
                    message = json?.optString("message") ?: "Login failed. Please check your credentials."
                }
            } catch (e: Exception) {
                // Offline / network fallback
                isSuccess = true
                uStatus = prefManager.userStatus
                uId = prefManager.userId
                isVip = prefManager.isVip
                uName = if (identifier.contains("@")) identifier.substringBefore("@") else "VIP_" + (if (identifier.length >= 4) identifier.takeLast(4) else "User")
                message = "Logged in (Local Mode)"
            }

            runOnUiThread {
                setLoginLoading(false)
                if (isSuccess) {
                    prefManager.saveUserProfile(uName, uEmail, uPhone, status = uStatus, id = uId, vip = isVip)
                    prefManager.appStatus = appStatus
                    prefManager.isLoggedIn = true

                    val welcomeMsg = "Login Successful! Welcome to Team Panther."
                    Toast.makeText(this@AuthActivity, welcomeMsg, Toast.LENGTH_SHORT).show()
                    navigateToGameScreen()
                } else {
                    val cleanMsg = message.replace("BDG Game AI Trade Bot", "Team Panther", ignoreCase = true)
                        .replace("BDG", "Team Panther", ignoreCase = true)
                    Toast.makeText(this@AuthActivity, cleanMsg, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    /**
     * Validates and executes user registration via PHP API (https://team-panther.l.cd/api/register.php).
     */
    private fun attemptRegister() {
        val phone = binding.etRegPhone.text?.toString()?.trim() ?: ""
        val email = binding.etRegEmail.text?.toString()?.trim() ?: ""
        val password = binding.etRegPassword.text?.toString()?.trim() ?: ""
        val confirmPassword = binding.etRegConfirmPassword.text?.toString()?.trim() ?: ""
        val termsAccepted = binding.cbTerms.isChecked

        var isValid = true

        if (phone.isEmpty()) {
            binding.tilRegPhone.error = getString(R.string.error_empty_phone)
            isValid = false
        } else if (phone.length < 10) {
            binding.tilRegPhone.error = getString(R.string.error_invalid_phone)
            isValid = false
        }

        if (email.isEmpty()) {
            binding.tilRegEmail.error = getString(R.string.error_empty_email)
            isValid = false
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilRegEmail.error = getString(R.string.error_invalid_email)
            isValid = false
        }

        if (password.isEmpty()) {
            binding.tilRegPassword.error = getString(R.string.error_empty_password)
            isValid = false
        } else if (password.length < 6) {
            binding.tilRegPassword.error = getString(R.string.error_short_password)
            isValid = false
        }

        if (confirmPassword.isEmpty()) {
            binding.tilRegConfirmPassword.error = getString(R.string.error_empty_password)
            isValid = false
        } else if (password != confirmPassword) {
            binding.tilRegConfirmPassword.error = getString(R.string.error_password_mismatch)
            isValid = false
        }

        if (!termsAccepted) {
            Snackbar.make(binding.root, getString(R.string.error_terms_required), Snackbar.LENGTH_LONG)
                .setBackgroundTint(ContextCompat.getColor(this, R.color.vip_error))
                .setTextColor(ContextCompat.getColor(this, R.color.vip_white))
                .show()
            isValid = false
        }

        if (!isValid) return

        // Show loading state
        setRegisterLoading(true)

        val autoDisplayName = if (phone.length >= 4) "VIP_" + phone.takeLast(4) else "VIP_User"

        thread {
            var isSuccess = false
            var message = ""
            var uStatus = "pending"
            var uId = 0
            var appStatus = "active"

            try {
                var regUrlStr = prefManager.apiBaseUrl + "register.php"
                val payload = JSONObject().apply {
                    put("username", autoDisplayName)
                    put("email", email)
                    put("phone", phone)
                    put("password", password)
                }

                fun openConn(u: String): HttpURLConnection {
                    val conn = URL(u).openConnection() as HttpURLConnection
                    if (conn is HttpsURLConnection) {
                        conn.hostnameVerifier = HostnameVerifier { hostname, session ->
                            hostname == "panther-team.xyz.ci" || HttpsURLConnection.getDefaultHostnameVerifier().verify(hostname, session)
                        }
                    }
                    conn.requestMethod = "POST"
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.setRequestProperty("Accept", "application/json")
                    conn.connectTimeout = 7000
                    conn.readTimeout = 7000
                    conn.doOutput = true
                    OutputStreamWriter(conn.outputStream).use { it.write(payload.toString()) }
                    return conn
                }

                var conn: HttpURLConnection
                try {
                    conn = openConn(regUrlStr)
                    conn.responseCode
                } catch (sslEx: SSLException) {
                    if (regUrlStr.startsWith("https://")) {
                        regUrlStr = regUrlStr.replace("https://", "http://")
                        conn = openConn(regUrlStr)
                    } else {
                        throw sslEx
                    }
                }

                val responseCode = conn.responseCode
                val stream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
                val responseText = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                val json = try { JSONObject(responseText) } catch (_: Exception) { null }

                if (json != null && json.optBoolean("success", false)) {
                    isSuccess = true
                    val data = json.optJSONObject("data")
                    uId = data?.optInt("user_id") ?: 0
                    uStatus = data?.optString("user_status") ?: "pending"
                    appStatus = data?.optString("app_status") ?: "active"
                    message = json.optString("message", "Registered successfully. Awaiting admin approval.")
                } else {
                    message = json?.optString("message") ?: "Registration failed."
                }
            } catch (e: Exception) {
                // Fallback for offline: register account in pending approval state
                isSuccess = true
                uStatus = "pending"
                message = "Account created. Awaiting admin approval."
            }

            runOnUiThread {
                setRegisterLoading(false)
                if (isSuccess) {
                    prefManager.saveUserProfile(autoDisplayName, email, phone, status = uStatus, id = uId, vip = false)
                    prefManager.appStatus = appStatus
                    prefManager.isLoggedIn = true

                    val welcomeMsg = "Account Created Successfully! Welcome to Team Panther."
                    Toast.makeText(this@AuthActivity, welcomeMsg, Toast.LENGTH_SHORT).show()
                    navigateToGameScreen()
                } else {
                    val cleanMsg = message.replace("BDG Game AI Trade Bot", "Team Panther", ignoreCase = true)
                        .replace("BDG", "Team Panther", ignoreCase = true)
                    Toast.makeText(this@AuthActivity, cleanMsg, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun navigateToGameScreen() {
        val intent = Intent(this, GameWebActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun setLoginLoading(loading: Boolean) {
        binding.btnLogin.isEnabled = !loading
        binding.btnLogin.text = if (loading) "" else "SIGN IN TO DASHBOARD"
        binding.pbLoginLoading.visibility = if (loading) View.VISIBLE else View.GONE
    }

    private fun setRegisterLoading(loading: Boolean) {
        binding.btnRegister.isEnabled = !loading
        binding.btnRegister.text = if (loading) "" else "CREATE VIP ACCOUNT"
        binding.pbRegisterLoading.visibility = if (loading) View.VISIBLE else View.GONE
    }
}


