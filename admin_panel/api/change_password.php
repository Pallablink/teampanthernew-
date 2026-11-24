<?php
/**
 * API: Change User Password
 * Method: POST
 * Parameters: identifier (phone or email), old_password, new_password
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

$identifier  = trim($json['identifier'] ?? $_POST['identifier'] ?? '');
$oldPassword = trim($json['old_password'] ?? $_POST['old_password'] ?? '');
$newPassword = trim($json['new_password'] ?? $_POST['new_password'] ?? '');

if (empty($identifier) || empty($oldPassword) || empty($newPassword)) {
    sendJsonResponse(false, 'Identifier, current password, and new password are required.', null, 400);
}

if (strlen($newPassword) < 6) {
    sendJsonResponse(false, 'New password must be at least 6 characters.', null, 400);
}

$pdo = getDbConnection();

try {
    $stmt = $pdo->prepare("SELECT id, password_hash FROM users WHERE phone = ? OR email = ? OR username = ? LIMIT 1");
    $stmt->execute([$identifier, $identifier, $identifier]);
    $user = $stmt->fetch();

    if (!$user) {
        sendJsonResponse(false, 'User not found.', null, 404);
    }

    if (!password_verify($oldPassword, $user['password_hash'])) {
        sendJsonResponse(false, 'Current password is incorrect.', null, 401);
    }

    $newHash = password_hash($newPassword, PASSWORD_BCRYPT);
    $upStmt = $pdo->prepare("UPDATE users SET password_hash = ? WHERE id = ?");
    $upStmt->execute([$newHash, $user['id']]);

    sendJsonResponse(true, 'Password updated successfully.', null, 200);

} catch (PDOException $e) {
    sendJsonResponse(false, 'Database error: ' . $e->getMessage(), null, 500);
}


