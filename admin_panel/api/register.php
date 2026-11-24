<?php
/**
 * API: User Registration
 * Method: POST
 * Parameters: username, phone, email, password
 * Target Domain: https://team-panther.l.cd/
 */

header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: POST, OPTIONS');
header('Access-Control-Allow-Headers: Content-Type, Authorization');

if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
    exit(0);
}

require_once __DIR__ . '/../config/db.php';

// Accept both JSON payload and standard x-www-form-urlencoded
$rawInput = file_get_contents('php://input');
$json = json_decode($rawInput, true);

$username = trim($json['username'] ?? $_POST['username'] ?? '');
$phone    = trim($json['phone'] ?? $_POST['phone'] ?? '');
$email    = strtolower(trim($json['email'] ?? $_POST['email'] ?? ''));
$password = trim($json['password'] ?? $_POST['password'] ?? '');

// If username is not passed by app, auto-generate VIP handle from phone
if (empty($username) && !empty($phone)) {
    $username = 'VIP_' . substr(preg_replace('/\D/', '', $phone), -4);
}

if (empty($phone) || empty($email) || empty($password)) {
    sendJsonResponse(false, 'All fields (phone, email, password) are required.', null, 400);
}

// Basic format validations
if (strlen($username) < 3) {
    sendJsonResponse(false, 'Username must be at least 3 characters.', null, 400);
}

if (!filter_var($email, FILTER_VALIDATE_EMAIL)) {
    sendJsonResponse(false, 'Invalid email address format.', null, 400);
}

if (strlen($password) < 6) {
    sendJsonResponse(false, 'Password must be at least 6 characters.', null, 400);
}

$pdo = getDbConnection();

// Check if registration is enabled by Admin
$regEnabled = getAppSetting($pdo, 'registration_enabled', '1');
if ($regEnabled !== '1') {
    sendJsonResponse(false, 'Registrations are currently closed by administrator.', null, 403);
}

// Check App Status (active, maintenance, off)
$appStatus = getAppSetting($pdo, 'app_status', 'active');
if ($appStatus === 'off') {
    sendJsonResponse(false, 'TeamPanther service is currently disabled by administrator.', null, 503);
}

try {
    // Check if email, username or phone is already taken
    $checkStmt = $pdo->prepare("SELECT id, email, username, phone FROM users WHERE email = ? OR username = ? OR phone = ? LIMIT 1");
    $checkStmt->execute([$email, $username, $phone]);
    $existing = $checkStmt->fetch();

    if ($existing) {
        if (strtolower($existing['email']) === $email) {
            sendJsonResponse(false, 'This email address is already registered.', null, 409);
        }
        if (strtolower($existing['username']) === strtolower($username)) {
            sendJsonResponse(false, 'This username is already taken.', null, 409);
        }
        if ($existing['phone'] === $phone) {
            sendJsonResponse(false, 'This phone number is already registered.', null, 409);
        }
    }

    // Check if Admin Approval is required
    $requireApproval = getAppSetting($pdo, 'require_admin_approval', '1') === '1';
    $autoVip = getAppSetting($pdo, 'vip_auto_activation', '0') === '1' ? 1 : 0;

    $initialStatus = $requireApproval ? 'pending' : 'active';
    $initialVip    = $requireApproval ? 0 : $autoVip;

    $passwordHash = password_hash($password, PASSWORD_BCRYPT);

    $insertStmt = $pdo->prepare("INSERT INTO users (username, phone, email, password_hash, is_vip, status) VALUES (?, ?, ?, ?, ?, ?)");
    $insertStmt->execute([$username, $phone, $email, $passwordHash, $initialVip, $initialStatus]);
    $userId = $pdo->lastInsertId();

    // Log action
    $logStmt = $pdo->prepare("INSERT INTO bot_logs (user_id, action, details, ip_address) VALUES (?, 'USER_REGISTERED', ?, ?)");
    $logStmt->execute([$userId, "User registered via App with username: $username (Status: $initialStatus)", $_SERVER['REMOTE_ADDR'] ?? '']);

    $responseMsg = $requireApproval
        ? 'Account registered successfully! Admin approval is required before bot automation can be started.'
        : 'Account created successfully! Welcome to Team Panther.';

    sendJsonResponse(true, $responseMsg, [
        'user_id'           => (int)$userId,
        'username'          => $username,
        'email'             => $email,
        'phone'             => $phone,
        'is_vip'            => (bool)$initialVip,
        'status'            => $initialStatus,
        'requires_approval' => $requireApproval,
        'app_status'        => $appStatus,
        'target_url'        => getAppSetting($pdo, 'target_web_url', 'https://kwg08.com/')
    ], 201);

} catch (PDOException $e) {
    sendJsonResponse(false, 'Registration failed due to a server error: ' . $e->getMessage(), null, 500);
}


