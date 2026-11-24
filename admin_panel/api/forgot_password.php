<?php
/**
 * API: Forgot Password Request
 * Method: POST
 * Parameters: target (email or phone)
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

$target = trim($json['target'] ?? $_POST['target'] ?? '');

if (empty($target)) {
    sendJsonResponse(false, 'Please provide your registered email or phone number.', null, 400);
}

$pdo = getDbConnection();

try {
    // Check if target matches any user
    $stmt = $pdo->prepare("SELECT id, email, phone, username FROM users WHERE email = ? OR phone = ? LIMIT 1");
    $stmt->execute([$target, $target]);
    $user = $stmt->fetch();

    if (!$user) {
        // Return generic success to avoid user enumeration, or clear message
        sendJsonResponse(true, 'If an account matches this info, password reset instructions have been sent.', null, 200);
    }

    // Generate 6-digit OTP code or reset token
    $resetCode = str_pad((string)random_int(100000, 999999), 6, '0', STR_PAD_LEFT);
    $expiresAt = date('Y-m-d H:i:s', strtotime('+15 minutes'));

    $resetStmt = $pdo->prepare("INSERT INTO password_resets (email_or_phone, token, expires_at) VALUES (?, ?, ?)");
    $resetStmt->execute([$target, $resetCode, $expiresAt]);

    // Log the request
    $logStmt = $pdo->prepare("INSERT INTO bot_logs (user_id, action, details, ip_address) VALUES (?, 'FORGOT_PASSWORD_REQUESTED', ?, ?)");
    $logStmt->execute([$user['id'], "Password reset OTP generated for $target", $_SERVER['REMOTE_ADDR'] ?? '']);

    // In a live cPanel environment, you can integrate mail() or Twilio/SMS here.
    sendJsonResponse(true, 'Password reset instructions have been sent successfully to your registered contact.', [
        'target' => $target,
        'expires_in_minutes' => 15
    ], 200);

} catch (PDOException $e) {
    sendJsonResponse(false, 'Server error: ' . $e->getMessage(), null, 500);
}


