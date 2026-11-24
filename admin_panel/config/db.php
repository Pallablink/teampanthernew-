<?php
/**
 * Database Configuration & Helper Utilities
 * TeamPanther - Admin Panel & API Backend
 */

if (session_status() === PHP_SESSION_NONE) {
    session_start();
}

// -------------------------------------------------------------
// cPanel Database Credentials (Change these to your cPanel details)
// -------------------------------------------------------------
define('DB_HOST', 'localhost');
define('DB_NAME', 'teampanther_db');       // e.g. cpaneluser_teampanther
define('DB_USER', 'root');                // e.g. cpaneluser_admin
define('DB_PASS', '');                    // e.g. YourStrongPassword123#
define('DB_CHARSET', 'utf8mb4');

// -------------------------------------------------------------
// App URL Configuration
// -------------------------------------------------------------
define('APP_NAME', 'TeamPanther Admin');
define('BASE_URL', (isset($_SERVER['HTTPS']) && $_SERVER['HTTPS'] === 'on' ? 'https' : 'http') . '://' . ($_SERVER['HTTP_HOST'] ?? 'localhost') . '/admin_panel');

/**
 * Get PDO Database Connection
 */
function getDbConnection() {
    static $pdo = null;
    if ($pdo === null) {
        $dsn = "mysql:host=" . DB_HOST . ";dbname=" . DB_NAME . ";charset=" . DB_CHARSET;
        $options = [
            PDO::ATTR_ERRMODE            => PDO::ERRMODE_EXCEPTION,
            PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
            PDO::ATTR_EMULATE_PREPARES   => false,
        ];
        try {
            $pdo = new PDO($dsn, DB_USER, DB_PASS, $options);
        } catch (PDOException $e) {
            if (strpos($_SERVER['REQUEST_URI'] ?? '', '/api/') !== false) {
                sendJsonResponse(false, 'Database connection error: ' . $e->getMessage(), null, 500);
            } else {
                die('<div style="font-family:sans-serif;padding:30px;background:#111;color:#f87171;"><h2>Database Connection Error</h2><p>Please check your database credentials in <code>config/db.php</code>.</p><p>' . htmlspecialchars($e->getMessage()) . '</p></div>');
            }
        }
    }
    return $pdo;
}

/**
 * Standard JSON API response helper
 */
function sendJsonResponse($success, $message = '', $data = null, $httpCode = 200) {
    http_response_code($httpCode);
    header('Content-Type: application/json; charset=UTF-8');
    echo json_encode([
        'success'   => $success,
        'message'   => $message,
        'data'      => $data,
        'timestamp' => time()
    ], JSON_UNESCAPED_UNICODE | JSON_PRETTY_PRINT);
    exit;
}

/**
 * Fetch dynamic app setting from database
 */
function getAppSetting($pdo, $key, $default = '') {
    try {
        $stmt = $pdo->prepare("SELECT setting_value FROM app_settings WHERE setting_key = ? LIMIT 1");
        $stmt->execute([$key]);
        $val = $stmt->fetchColumn();
        return $val !== false ? $val : $default;
    } catch (Exception $e) {
        return $default;
    }
}

/**
 * Update app setting in database
 */
function updateAppSetting($pdo, $key, $value) {
    $stmt = $pdo->prepare("INSERT INTO app_settings (setting_key, setting_value) VALUES (?, ?) ON DUPLICATE KEY UPDATE setting_value = ?");
    return $stmt->execute([$key, $value, $value]);
}

/**
 * Check if Admin is logged in
 */
function isAdminLoggedIn() {
    return isset($_SESSION['admin_logged_in']) && $_SESSION['admin_logged_in'] === true;
}

/**
 * Require admin authentication for protected pages
 */
function requireAdminLogin() {
    if (!isAdminLoggedIn()) {
        header('Location: login.php');
        exit;
    }
}


