<?php
$pageTitle = 'TeamPanther - System Settings';
require_once __DIR__ . '/header.php';

$pdo = getDbConnection();
$feedback = '';
$feedbackType = 'success';

// Handle Settings Update
if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    $action = $_POST['action'] ?? '';

    if ($action === 'save_settings') {
        $appStatus = trim($_POST['app_status'] ?? 'active');
        if (!in_array($appStatus, ['active', 'maintenance', 'off'])) {
            $appStatus = 'active';
        }

        $settingsToSave = [
            'app_name'               => 'TeamPanther',
            'app_version'            => '1.0.0',
            'target_web_url'         => trim($_POST['target_web_url'] ?? 'https://kwg08.com/'),
            'support_contact'        => trim($_POST['support_contact'] ?? ''),
            'app_status'             => $appStatus,
            'maintenance_mode'       => ($appStatus === 'maintenance') ? '1' : '0',
            'maintenance_message'    => trim($_POST['maintenance_message'] ?? 'TeamPanther is undergoing scheduled maintenance. Bot automation is temporarily paused.'),
            'require_admin_approval' => isset($_POST['require_admin_approval']) ? '1' : '0',
            'registration_enabled'   => isset($_POST['registration_enabled']) ? '1' : '0',
            'vip_auto_activation'   => isset($_POST['vip_auto_activation']) ? '1' : '0',
            'bot_engine_status'      => trim($_POST['bot_engine_status'] ?? 'active'),
            'bot_speed_delay'        => trim($_POST['bot_speed_delay'] ?? '1000')
        ];

        foreach ($settingsToSave as $k => $v) {
            updateAppSetting($pdo, $k, $v);
        }

        // Log setting change
        $logStmt = $pdo->prepare("INSERT INTO bot_logs (action, details, ip_address) VALUES ('SETTINGS_UPDATED', ?, ?)");
        $logStmt->execute(["Admin updated system settings. App Status: $appStatus", $_SERVER['REMOTE_ADDR'] ?? '']);

        $feedback = 'System settings saved and applied successfully.';
    } elseif ($action === 'change_admin_password') {
        $currentPass = trim($_POST['current_password'] ?? '');
        $newPass     = trim($_POST['new_password'] ?? '');
        $confirmPass = trim($_POST['confirm_password'] ?? '');

        if (empty($newPass) || strlen($newPass) < 6) {
            $feedback = 'New password must be at least 6 characters.';
            $feedbackType = 'error';
        } elseif ($newPass !== $confirmPass) {
            $feedback = 'New passwords do not match.';
            $feedbackType = 'error';
        } else {
            $adminId = $_SESSION['admin_id'];
            $checkStmt = $pdo->prepare("SELECT password_hash FROM admin_users WHERE id = ?");
            $checkStmt->execute([$adminId]);
            $hash = $checkStmt->fetchColumn();

            if (password_verify($currentPass, $hash)) {
                $newHash = password_hash($newPass, PASSWORD_BCRYPT);
                $upd = $pdo->prepare("UPDATE admin_users SET password_hash = ? WHERE id = ?");
                $upd->execute([$newHash, $adminId]);
                $feedback = 'Admin password successfully updated.';
            } else {
                $feedback = 'Current admin password is incorrect.';
                $feedbackType = 'error';
            }
        }
    }
}

// Fetch all current settings
$stmt = $pdo->query("SELECT setting_key, setting_value FROM app_settings");
$currentSettings = $stmt->fetchAll(PDO::FETCH_KEY_PAIR);
$currentAppStatus = $currentSettings['app_status'] ?? (($currentSettings['maintenance_mode'] ?? '0') === '1' ? 'maintenance' : 'active');
?>

<?php if (!empty($feedback)): ?>
    <div class="mb-6 p-4 rounded-sm <?= $feedbackType === 'error' ? 'bg-red-500/10 border-red-500/30 text-red-400' : 'bg-emerald-500/10 border-emerald-500/30 text-emerald-400' ?> border text-sm flex items-center justify-between">
        <span><i class="fa-solid <?= $feedbackType === 'error' ? 'fa-triangle-exclamation' : 'fa-circle-check' ?> mr-2"></i><?= htmlspecialchars($feedback) ?></span>
        <button onclick="this.parentElement.remove()" class="text-slate-400 hover:text-white">&times;</button>
    </div>
<?php endif; ?>

<div class="grid grid-cols-1 lg:grid-cols-3 gap-6">

    <!-- MAIN CONFIGURATION FORM (2 COLS) -->
    <div class="lg:col-span-2 space-y-6">
        <form method="POST" class="space-y-6">
            <input type="hidden" name="action" value="save_settings">

            <!-- Card 0: App Status (ACTIVE / MAINTENANCE / OFF) -->
            <div class="glass-card rounded-sm p-6 border-l-4 <?= $currentAppStatus === 'active' ? 'border-l-emerald-500' : ($currentAppStatus === 'maintenance' ? 'border-l-amber-500' : 'border-l-red-500') ?>">
                <div class="flex items-center justify-between mb-4 pb-3 border-b border-slate-800">
                    <div class="flex items-center gap-2">
                        <i class="fa-solid fa-power-off text-red-400"></i>
                        <h3 class="text-sm font-bold text-white uppercase tracking-wider">Application Global Status</h3>
                    </div>
                    <span class="px-3 py-1 rounded-full text-xs font-bold uppercase <?= $currentAppStatus === 'active' ? 'bg-emerald-500/20 text-emerald-400 border border-emerald-500/40' : ($currentAppStatus === 'maintenance' ? 'bg-amber-500/20 text-amber-400 border border-amber-500/40' : 'bg-red-500/20 text-red-400 border border-red-500/40') ?>">
                        Current: <?= strtoupper($currentAppStatus) ?>
                    </span>
                </div>

                <div class="space-y-4">
                    <div>
                        <label class="block text-xs font-semibold text-slate-200 mb-1.5">Select App Operational State</label>
                        <select name="app_status" class="w-full px-4 py-3 bg-slate-900 border border-slate-700 rounded-sm text-xs font-bold text-white focus:outline-none focus:border-red-500">
                            <option value="active" <?= $currentAppStatus === 'active' ? 'selected' : '' ?>>🟢 ACTIVE (App &amp; Bot Automation Online)</option>
                            <option value="maintenance" <?= $currentAppStatus === 'maintenance' ? 'selected' : '' ?>>🟡 MAINTENANCE MODE (Bot Automation Paused with Notice)</option>
                            <option value="off" <?= $currentAppStatus === 'off' ? 'selected' : '' ?>>🔴 OFF (Service Disabled)</option>
                        </select>
                        <span class="text-[11px] text-slate-400 mt-1 block">When set to Maintenance or Off, the Android App will prevent starting bot automation and display the maintenance alert dialog.</span>
                    </div>

                    <div>
                        <label class="block text-xs font-semibold text-slate-200 mb-1.5">Maintenance Announcement Message</label>
                        <input type="text" name="maintenance_message" value="<?= htmlspecialchars($currentSettings['maintenance_message'] ?? 'TeamPanther is undergoing scheduled maintenance. Bot automation is temporarily paused.') ?>"
                               class="w-full px-3.5 py-2.5 bg-slate-900 border border-slate-700 rounded-sm text-xs text-white focus:outline-none focus:border-red-500">
                    </div>
                </div>
            </div>

            <!-- Card 1: Official Support & Contact Desk -->
            <div class="glass-card rounded-sm p-6">
                <div class="flex items-center gap-2 mb-4 pb-3 border-b border-slate-800">
                    <i class="fa-solid fa-headset text-red-400"></i>
                    <h3 class="text-sm font-bold text-white uppercase tracking-wider">Official Support &amp; Help Desk</h3>
                </div>

                <div>
                    <label class="block text-xs font-semibold text-slate-300 mb-1.5">Official Support Email / Telegram Desk Link</label>
                    <input type="text" name="support_contact" value="<?= htmlspecialchars($currentSettings['support_contact'] ?? '') ?>" placeholder="e.g. https://t.me/teampanther or support@teampanther.com"
                           class="w-full px-3.5 py-2.5 bg-slate-900 border border-slate-700 rounded-sm text-xs text-white focus:outline-none focus:border-red-500">
                    <span class="text-[11px] text-slate-400 mt-1.5 block">Displayed to users when contacting VIP support or when their account is awaiting admin approval.</span>
                </div>

                <div class="mt-4">
                    <label class="block text-xs font-semibold text-slate-300 mb-1.5">Target Game / Web Platform URL</label>
                    <input type="text" name="target_web_url" value="<?= htmlspecialchars($currentSettings['target_web_url'] ?? 'https://kwg08.com/') ?>" placeholder="https://kwg08.com/"
                           class="w-full px-3.5 py-2.5 bg-slate-900 border border-slate-700 rounded-sm text-xs text-white focus:outline-none focus:border-red-500">
                    <span class="text-[11px] text-slate-400 mt-1.5 block">The game URL loaded inside the TeamPanther Android App WebView.</span>
                </div>
            </div>

            <!-- Card 2: Auth Policies & Admin Approval -->
            <div class="glass-card rounded-sm p-6">
                <div class="flex items-center gap-2 mb-5 pb-3 border-b border-slate-800">
                    <i class="fa-solid fa-user-shield text-amber-400"></i>
                    <h3 class="text-sm font-bold text-white uppercase tracking-wider">User Registration &amp; Admin Approval Policy</h3>
                </div>

                <div class="space-y-4">
                    <!-- REQUIRE ADMIN APPROVAL TOGGLE -->
                    <div class="flex items-center justify-between p-4 rounded-sm bg-amber-500/10 border border-amber-500/30">
                        <div>
                            <div class="text-xs font-bold text-amber-300 flex items-center gap-2">
                                <i class="fa-solid fa-shield-halved"></i> Require Admin Approval on Registration
                            </div>
                            <div class="text-[11px] text-slate-300 mt-1">When enabled, newly registered users remain in <strong>PENDING</strong> status. The START AUTO button in the app will remain locked until an Admin approves the account in User Management.</div>
                        </div>
                        <label class="relative inline-flex items-center cursor-pointer ml-4">
                            <input type="checkbox" name="require_admin_approval" value="1" <?= ($currentSettings['require_admin_approval'] ?? '1') === '1' ? 'checked' : '' ?> class="sr-only peer">
                            <div class="w-11 h-6 bg-slate-800 peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-slate-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-amber-500"></div>
                        </label>
                    </div>

                    <div class="flex items-center justify-between p-3.5 rounded-sm bg-slate-900/60 border border-slate-800">
                        <div>
                            <div class="text-xs font-semibold text-white">Allow Public User Registrations</div>
                            <div class="text-[11px] text-slate-400">If disabled, the register tab in the app will reject sign-ups</div>
                        </div>
                        <label class="relative inline-flex items-center cursor-pointer">
                            <input type="checkbox" name="registration_enabled" value="1" <?= ($currentSettings['registration_enabled'] ?? '1') === '1' ? 'checked' : '' ?> class="sr-only peer">
                            <div class="w-11 h-6 bg-slate-800 peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-slate-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-red-600"></div>
                        </label>
                    </div>

                    <div class="flex items-center justify-between p-3.5 rounded-sm bg-slate-900/60 border border-slate-800">
                        <div>
                            <div class="text-xs font-semibold text-white">Auto-Grant VIP on Registration (If Approval Disabled)</div>
                            <div class="text-[11px] text-slate-400">Only applies if Require Admin Approval is turned OFF</div>
                        </div>
                        <label class="relative inline-flex items-center cursor-pointer">
                            <input type="checkbox" name="vip_auto_activation" value="1" <?= ($currentSettings['vip_auto_activation'] ?? '0') === '1' ? 'checked' : '' ?> class="sr-only peer">
                            <div class="w-11 h-6 bg-slate-800 peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-slate-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-amber-500"></div>
                        </label>
                    </div>
                </div>
            </div>

            <!-- Submit Button -->
            <div class="flex justify-end">
                <button type="submit" class="gradient-btn px-6 py-3 rounded-sm text-xs font-bold text-white shadow-xl shadow-red-600/30 hover:opacity-95 transition">
                    <i class="fa-solid fa-floppy-disk mr-2"></i> Save All Settings
                </button>
            </div>
        </form>
    </div>

    <!-- SIDEBAR: CHANGE ADMIN PASSWORD & INFO (1 COL) -->
    <div class="space-y-6">

        <!-- Change Password Card -->
        <div class="glass-card rounded-sm p-6">
            <div class="flex items-center gap-2 mb-4 pb-3 border-b border-slate-800">
                <i class="fa-solid fa-key text-red-400"></i>
                <h3 class="text-sm font-bold text-white uppercase tracking-wider">Change Admin Password</h3>
            </div>

            <form method="POST" class="space-y-3.5">
                <input type="hidden" name="action" value="change_admin_password">

                <div>
                    <label class="block text-xs font-semibold text-slate-300 mb-1">Current Password</label>
                    <input type="password" name="current_password" required
                           class="w-full px-3 py-2 bg-slate-900 border border-slate-700 rounded-sm text-xs text-white focus:outline-none focus:border-red-500">
                </div>

                <div>
                    <label class="block text-xs font-semibold text-slate-300 mb-1">New Password</label>
                    <input type="password" name="new_password" required minlength="6"
                           class="w-full px-3 py-2 bg-slate-900 border border-slate-700 rounded-sm text-xs text-white focus:outline-none focus:border-red-500">
                </div>

                <div>
                    <label class="block text-xs font-semibold text-slate-300 mb-1">Confirm New Password</label>
                    <input type="password" name="confirm_password" required minlength="6"
                           class="w-full px-3 py-2 bg-slate-900 border border-slate-700 rounded-sm text-xs text-white focus:outline-none focus:border-red-500">
                </div>

                <button type="submit" class="w-full py-2.5 bg-slate-800 hover:bg-slate-700 border border-slate-700 rounded-sm text-xs font-bold text-white transition mt-2">
                    Update Password
                </button>
            </form>
        </div>

    </div>
</div>

<?php require_once __DIR__ . '/footer.php'; ?>




