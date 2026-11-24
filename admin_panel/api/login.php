<?php
/**
 * API: User Login
 * Method: POST
 * Parameters: identifier (email, phone, or username), password
 * Target Domain: https://team-panther.l.cd/
 */

header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: POST, OPTIONS');
header('Access-Control-Allow-Headers: Content-Type, Authorization');

if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
    exit(0);
}

require_once __DIR__ . '/../config/db.php';

$rawInput = file_get_contents('php://input');
$json = json_decode($rawInput, true);

$identifier = trim($json['identifier'] ?? $_POST['identifier'] ?? '');
$password   = trim($json['password'] ?? $_POST['password'] ?? '');

if (empty($identifier) || empty($password)) {
    sendJsonResponse(false, 'Email/Phone and Password are required.', null, 400);
}

$pdo = getDbConnection();

// Check App Status (active, maintenance, off)
$appStatus = getAppSetting($pdo, 'app_status', 'active');
$maintenanceMsg = getAppSetting($pdo, 'maintenance_message', 'TeamPanther is undergoing scheduled system upgrades. Bot automation is temporarily paused.');

if ($appStatus === 'off') {
    sendJsonResponse(false, 'TeamPanther service is currently disabled by administrator.', ['app_status' => 'off'], 503);
}

try {
    // Find user by either email OR username OR phone
    $stmt = $pdo->prepare("SELECT id, username, phone, email, password_hash, avatar, is_vip, status FROM users WHERE email = ? OR username = ? OR phone = ? LIMIT 1");
    $stmt->execute([$identifier, $identifier, $identifier]);
    $user = $stmt->fetch();

    if (!$user) {
        sendJsonResponse(false, 'No account found with this email, phone, or username.', null, 404);
    }

    // Verify Password
    if (!password_verify($password, $user['password_hash'])) {
        sendJsonResponse(false, 'Incorrect password. Please try again.', null, 401);
    }

    // Check account status
    if ($user['status'] === 'banned') {
        sendJsonResponse(false, 'Your account has been permanently banned by administrator.', ['status' => 'banned'], 403);
    }
    if ($user['status'] === 'suspended') {
        sendJsonResponse(false, 'Your account is suspended. Contact support for assistance.', ['status' => 'suspended'], 403);
    }
    if ($user['status'] === 'inactive') {
        sendJsonResponse(false, 'Your account is currently inactive. Contact administrator.', ['status' => 'inactive'], 403);
    }

    // Update last login timestamp
    $updateStmt = $pdo->prepare("UPDATE users SET last_login = NOW() WHERE id = ?");
    $updateStmt->execute([$user['id']]);

    // Log successful login
    $logStmt = $pdo->prepare("INSERT INTO bot_logs (user_id, action, details, ip_address) VALUES (?, 'USER_LOGIN', 'User logged in successfully', ?)");
    $logStmt->execute([$user['id'], $_SERVER['REMOTE_ADDR'] ?? '']);

    $loginMsg = ($user['status'] === 'pending')
        ? 'Login successful. Note: Your account is pending admin approval before bot can be started.'
        : 'Login successful! Welcome to Team Panther.';

    sendJsonResponse(true, $loginMsg, [
        'user_id'             => (int)$user['id'],
        'username'            => $user['username'],
        'email'               => $user['email'],
        'phone'               => $user['phone'],
        'is_vip'              => (bool)$user['is_vip'],
        'status'              => $user['status'], // 'pending', 'active', etc.
        'app_status'          => $appStatus,     // 'active', 'maintenance', 'off'
        'maintenance_message' => $maintenanceMsg,
        'target_url'          => getAppSetting($pdo, 'target_web_url', 'https://kwg08.com/')
    ], 200);

} catch (PDOException $e) {
    sendJsonResponse(false, 'Authentication error: ' . $e->getMessage(), null, 500);
}


