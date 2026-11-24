<?php
/**
 * API: Get Public App Settings & Live User Status Sync
 * Method: GET
 * Parameters (Optional): user_id, phone, identifier
 * Target Domain: https://team-panther.l.cd/
 */

header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: GET, OPTIONS');
header('Access-Control-Allow-Headers: Content-Type, Authorization');

if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
    exit(0);
}

require_once __DIR__ . '/../config/db.php';

$pdo = getDbConnection();

try {
    $stmt = $pdo->query("SELECT setting_key, setting_value FROM app_settings");
    $rawSettings = $stmt->fetchAll(PDO::FETCH_KEY_PAIR);

    $appStatus = $rawSettings['app_status'] ?? (($rawSettings['maintenance_mode'] ?? '0') === '1' ? 'maintenance' : 'active');

    $response = [
        'app_name'               => $rawSettings['app_name'] ?? 'TeamPanther',
        'app_version'            => $rawSettings['app_version'] ?? '1.0.0',
        'app_status'             => $appStatus, // 'active', 'maintenance', 'off'
        'target_web_url'         => $rawSettings['target_web_url'] ?? 'https://kwg08.com/',
        'maintenance_mode'       => ($appStatus === 'maintenance' || ($rawSettings['maintenance_mode'] ?? '0') === '1'),
        'maintenance_message'    => $rawSettings['maintenance_message'] ?? 'TeamPanther is undergoing scheduled maintenance. Bot automation is temporarily paused.',
        'require_admin_approval' => ($rawSettings['require_admin_approval'] ?? '1') === '1',
        'registration_enabled'   => ($rawSettings['registration_enabled'] ?? '1') === '1',
        'bot_engine_status'      => $rawSettings['bot_engine_status'] ?? 'active',
        'support_contact'        => $rawSettings['support_contact'] ?? 'support@teampanther.com'
    ];

    // Optional: Live User Status Check
    $userId = trim($_GET['user_id'] ?? '');
    $phone  = trim($_GET['phone'] ?? '');

    if (!empty($userId) || !empty($phone)) {
        $uStmt = $pdo->prepare("SELECT id, username, phone, status, is_vip FROM users WHERE id = ? OR phone = ? LIMIT 1");
        $uStmt->execute([$userId, $phone]);
        $userData = $uStmt->fetch();
        if ($userData) {
            $response['user_id']     = (int)$userData['id'];
            $response['user_status'] = $userData['status']; // 'pending', 'active', 'suspended', 'banned'
            $response['is_vip']      = (bool)$userData['is_vip'];
        }
    }

    sendJsonResponse(true, 'Settings fetched successfully.', $response);

} catch (PDOException $e) {
    sendJsonResponse(false, 'Failed to fetch settings: ' . $e->getMessage(), null, 500);
}


