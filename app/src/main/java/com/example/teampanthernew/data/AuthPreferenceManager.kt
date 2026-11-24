package com.example.teampanthernew.data

import android.content.Context
import android.content.SharedPreferences

class AuthPreferenceManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREF_NAME = "vip_auth_prefs"
        private const val KEY_REMEMBER_ME = "key_remember_me"
        private const val KEY_SAVED_IDENTIFIER = "key_saved_identifier"
        private const val KEY_SAVED_PASSWORD = "key_saved_password"
        private const val KEY_IS_LOGGED_IN = "key_is_logged_in"
        private const val KEY_USER_ID = "key_user_id"
        private const val KEY_USER_EMAIL = "key_user_email"
        private const val KEY_USER_NAME = "key_user_name"
        private const val KEY_USER_PHONE = "key_user_phone"
        private const val KEY_USER_STATUS = "key_user_status"
        private const val KEY_IS_VIP = "key_is_vip"
        private const val KEY_APP_STATUS = "key_app_status"
        private const val KEY_MAINTENANCE_MSG = "key_maintenance_msg"
        private const val KEY_USER_AVATAR_URI = "key_user_avatar_uri"
        private const val KEY_USER_AVATAR_TYPE = "key_user_avatar_type"
        private const val KEY_TARGET_WEB_URL = "key_target_web_url"
        private const val KEY_API_BASE_URL = "key_api_base_url"

        const val DEFAULT_API_BASE_URL = "https://panther-team.xyz.ci/api/"
        const val DEFAULT_TARGET_URL = "https://kwg08.com/"
    }

    var apiBaseUrl: String
        get() {
            val saved = prefs.getString(KEY_API_BASE_URL, DEFAULT_API_BASE_URL) ?: DEFAULT_API_BASE_URL
            if (saved.isBlank() || saved.contains("team-panther.l.cd", ignoreCase = true)) {
                prefs.edit().putString(KEY_API_BASE_URL, DEFAULT_API_BASE_URL).apply()
                return DEFAULT_API_BASE_URL
            }
            return saved
        }
        set(value) = prefs.edit().putString(KEY_API_BASE_URL, value).apply()

    var userId: Int
        get() = prefs.getInt(KEY_USER_ID, 0)
        set(value) = prefs.edit().putInt(KEY_USER_ID, value).apply()

    var userStatus: String
        get() = prefs.getString(KEY_USER_STATUS, "pending") ?: "pending"
        set(value) = prefs.edit().putString(KEY_USER_STATUS, value).apply()

    var isVip: Boolean
        get() = prefs.getBoolean(KEY_IS_VIP, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_VIP, value).apply()

    var appStatus: String
        get() = prefs.getString(KEY_APP_STATUS, "active") ?: "active"
        set(value) = prefs.edit().putString(KEY_APP_STATUS, value).apply()

    var maintenanceMessage: String
        get() = prefs.getString(KEY_MAINTENANCE_MSG, "TeamPanther is undergoing scheduled maintenance. Bot automation is temporarily paused.") ?: ""
        set(value) = prefs.edit().putString(KEY_MAINTENANCE_MSG, value).apply()

    var userAvatarUri: String?
        get() = prefs.getString(KEY_USER_AVATAR_URI, null)
        set(value) = prefs.edit().putString(KEY_USER_AVATAR_URI, value).apply()

    var userAvatarType: String
        get() = prefs.getString(KEY_USER_AVATAR_TYPE, "ROBOT") ?: "ROBOT"
        set(value) = prefs.edit().putString(KEY_USER_AVATAR_TYPE, value).apply()

    var targetWebUrl: String
        get() {
            val saved = prefs.getString(KEY_TARGET_WEB_URL, DEFAULT_TARGET_URL) ?: DEFAULT_TARGET_URL
            if (saved.isBlank() || !saved.startsWith("http") || saved.contains("bdg9", ignoreCase = true)) {
                prefs.edit().putString(KEY_TARGET_WEB_URL, DEFAULT_TARGET_URL).apply()
                return DEFAULT_TARGET_URL
            }
            return saved
        }
        set(value) {
            val safeUrl = if (value.isBlank() || !value.startsWith("http") || value.contains("bdg9", ignoreCase = true)) {
                DEFAULT_TARGET_URL
            } else {
                value
            }
            prefs.edit().putString(KEY_TARGET_WEB_URL, safeUrl).apply()
        }

    var lastKnownBalance: String
        get() = prefs.getString("KEY_LAST_KNOWN_BALANCE", "--") ?: "--"
        set(value) = prefs.edit().putString("KEY_LAST_KNOWN_BALANCE", value).apply()

    var targetProfit: Double
        get() = prefs.getString("KEY_TARGET_PROFIT", "500.0")?.toDoubleOrNull() ?: 500.0
        set(value) = prefs.edit().putString("KEY_TARGET_PROFIT", value.toString()).apply()

    var firstBetAmount: Double
        get() = prefs.getString("KEY_FIRST_BET", "1.0")?.toDoubleOrNull() ?: 1.0
        set(value) = prefs.edit().putString("KEY_FIRST_BET", value.toString()).apply()

    var maxLevels: Int
        get() = prefs.getInt("KEY_MAX_LEVELS", 10)
        set(value) = prefs.edit().putInt("KEY_MAX_LEVELS", value).apply()

    var is2xMultiplier: Boolean
        get() = prefs.getBoolean("KEY_IS_2X_MULT", true)
        set(value) = prefs.edit().putBoolean("KEY_IS_2X_MULT", value).apply()

    var selectedStrategy: String
        get() = prefs.getString("KEY_SELECTED_STRATEGY", "AI Neural Predictor") ?: "AI Neural Predictor"
        set(value) = prefs.edit().putString("KEY_SELECTED_STRATEGY", value).apply()

    var stopLossBudget: Double
        get() = prefs.getString("KEY_STOP_LOSS_BUDGET", "0.0")?.toDoubleOrNull() ?: 0.0
        set(value) = prefs.edit().putString("KEY_STOP_LOSS_BUDGET", value.toString()).apply()

    var isRememberMe: Boolean
        get() = prefs.getBoolean(KEY_REMEMBER_ME, false)
        set(value) = prefs.edit().putBoolean(KEY_REMEMBER_ME, value).apply()

    var savedIdentifier: String
        get() = prefs.getString(KEY_SAVED_IDENTIFIER, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SAVED_IDENTIFIER, value).apply()

    var savedPassword: String
        get() = prefs.getString(KEY_SAVED_PASSWORD, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SAVED_PASSWORD, value).apply()

    var isLoggedIn: Boolean
        get() = prefs.getBoolean(KEY_IS_LOGGED_IN, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_LOGGED_IN, value).apply()

    fun saveLoginCredentials(identifier: String, password: String, remember: Boolean) {
        isRememberMe = remember
        if (remember) {
            prefs.edit()
                .putBoolean(KEY_REMEMBER_ME, true)
                .putString(KEY_SAVED_IDENTIFIER, identifier)
                .putString(KEY_SAVED_PASSWORD, password)
                .apply()
        } else {
            prefs.edit()
                .putBoolean(KEY_REMEMBER_ME, false)
                .remove(KEY_SAVED_IDENTIFIER)
                .remove(KEY_SAVED_PASSWORD)
                .apply()
        }
    }

    fun saveUserProfile(
        name: String,
        email: String,
        phone: String,
        status: String = "pending",
        id: Int = 0,
        vip: Boolean = false
    ) {
        prefs.edit()
            .putString(KEY_USER_NAME, name)
            .putString(KEY_USER_EMAIL, email)
            .putString(KEY_USER_PHONE, phone)
            .putString(KEY_USER_STATUS, status)
            .putInt(KEY_USER_ID, id)
            .putBoolean(KEY_IS_VIP, vip)
            .putBoolean(KEY_IS_LOGGED_IN, true)
            .apply()
    }

    fun getUserName(): String = prefs.getString(KEY_USER_NAME, "") ?: ""
    fun getUserEmail(): String = prefs.getString(KEY_USER_EMAIL, "") ?: ""
    fun getUserPhone(): String = prefs.getString(KEY_USER_PHONE, "") ?: ""

    fun clearSession() {
        prefs.edit()
            .putBoolean(KEY_IS_LOGGED_IN, false)
            .remove(KEY_USER_NAME)
            .remove(KEY_USER_EMAIL)
            .remove(KEY_USER_PHONE)
            .remove(KEY_USER_STATUS)
            .remove(KEY_USER_ID)
            .remove(KEY_IS_VIP)
            .apply()
    }
}


