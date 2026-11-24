<?php
/**
 * API: Get User Profile & Status
 * Method: GET / POST
 * Parameters: user_id OR phone OR email
 */

header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: GET, POST, OPTIONS');
header('Access-Control-Allow-Headers: Content-Type, Authorization');

if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
    exit(0);
}

require_once __DIR__ . '/../config/db.php';

$rawInput = file_get_contents('php://input');
$json = json_decode($rawInput, true);

$userId = (int)($json['user_id'] ?? $_REQUEST['user_id'] ?? 0);
$identifier = trim($json['identifier'] ?? $_REQUEST['identifier'] ?? $_REQUEST['phone'] ?? $_REQUEST['email'] ?? '');

$pdo = getDbConnection();

try {
    if ($userId > 0) {
        $stmt = $pdo->prepare("SELECT id, username, phone, email, avatar, is_vip, status, created_at FROM users WHERE id = ? LIMIT 1");
        $stmt->execute([$userId]);
    } elseif (!empty($identifier)) {
        $stmt = $pdo->prepare("SELECT id, username, phone, email, avatar, is_vip, status, created_at FROM users WHERE phone = ? OR email = ? OR username = ? LIMIT 1");
        $stmt->execute([$identifier, $identifier, $identifier]);
    } else {
        sendJsonResponse(false, 'User ID, phone, or email is required.', null, 400);
    }

    $user = $stmt->fetch();
    if (!$user) {
        sendJsonResponse(false, 'User not found.', null, 404);
    }

    sendJsonResponse(true, 'Profile loaded.', [
        'user_id'    => (int)$user['id'],
        'username'   => $user['username'],
        'phone'      => $user['phone'],
        'email'      => $user['email'],
        'avatar'     => $user['avatar'] ?? 'default_robot',
        'is_vip'     => (bool)$user['is_vip'],
        'status'     => $user['status'], // active, inactive, banned, suspended
        'created_at' => $user['created_at']
    ]);

} catch (PDOException $e) {
    sendJsonResponse(false, 'Database error: ' . $e->getMessage(), null, 500);
}


