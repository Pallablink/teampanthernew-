<?php
/**
 * API: Submit User Feedback
 * Method: POST
 * Parameters: identifier, message, rating
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
$message    = trim($json['message'] ?? $_POST['message'] ?? '');
$rating     = (int)($json['rating'] ?? $_POST['rating'] ?? 5);

if (empty($message)) {
    sendJsonResponse(false, 'Feedback message cannot be empty.', null, 400);
}

$pdo = getDbConnection();

try {
    $userId = null;
    $phone = null;
    $email = null;

    if (!empty($identifier)) {
        $stmt = $pdo->prepare("SELECT id, phone, email FROM users WHERE phone = ? OR email = ? OR username = ? LIMIT 1");
        $stmt->execute([$identifier, $identifier, $identifier]);
        $user = $stmt->fetch();
        if ($user) {
            $userId = $user['id'];
            $phone = $user['phone'];
            $email = $user['email'];
        } else {
            $phone = $identifier;
        }
    }

    $insStmt = $pdo->prepare("INSERT INTO user_feedback (user_id, phone, email, rating, message) VALUES (?, ?, ?, ?, ?)");
    $insStmt->execute([$userId, $phone, $email, $rating, $message]);

    sendJsonResponse(true, 'Feedback received! Thank you for helping us improve TeamPanther.', null, 201);

} catch (PDOException $e) {
    sendJsonResponse(false, 'Failed to submit feedback: ' . $e->getMessage(), null, 500);
}


