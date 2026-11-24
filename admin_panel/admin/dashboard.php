<?php
$pageTitle = 'TeamPanther - Overview';
require_once __DIR__ . '/header.php';

$pdo = getDbConnection();

// Quick action handlers (POST from dashboard)
$message = '';
if ($_SERVER['REQUEST_METHOD'] === 'POST' && isset($_POST['action'])) {
    if ($_POST['action'] === 'set_app_status') {
        $newStatus = $_POST['app_status'] ?? 'active';
        if (in_array($newStatus, ['active', 'maintenance', 'off'])) {
            updateAppSetting($pdo, 'app_status', $newStatus);
            updateAppSetting($pdo, 'maintenance_mode', ($newStatus === 'maintenance') ? '1' : '0');
            $message = 'App Status set to ' . strtoupper($newStatus) . ' successfully.';
        }
    } elseif ($_POST['action'] === 'toggle_registration') {
        $current = getAppSetting($pdo, 'registration_enabled', '1');
        $new = $current === '1' ? '0' : '1';
        updateAppSetting($pdo, 'registration_enabled', $new);
        $message = 'Registration ' . ($new === '1' ? 'ENABLED' : 'DISABLED') . ' successfully.';
    } elseif ($_POST['action'] === 'toggle_approval') {
        $current = getAppSetting($pdo, 'require_admin_approval', '1');
        $new = $current === '1' ? '0' : '1';
        updateAppSetting($pdo, 'require_admin_approval', $new);
        $message = 'Admin Approval Requirement ' . ($new === '1' ? 'ENABLED' : 'DISABLED') . ' successfully.';
    }
}

// Fetch metrics
$totalUsers = $pdo->query("SELECT COUNT(*) FROM users")->fetchColumn() ?: 0;
$pendingUsers = $pdo->query("SELECT COUNT(*) FROM users WHERE status = 'pending'")->fetchColumn() ?: 0;
$vipUsers = $pdo->query("SELECT COUNT(*) FROM users WHERE is_vip = 1")->fetchColumn() ?: 0;
$activeUsers = $pdo->query("SELECT COUNT(*) FROM users WHERE status = 'active'")->fetchColumn() ?: 0;

$appStatus = getAppSetting($pdo, 'app_status', 'active');
$requireApproval = getAppSetting($pdo, 'require_admin_approval', '1') === '1';
$regEnabled = getAppSetting($pdo, 'registration_enabled', '1') === '1';
$botStatus = getAppSetting($pdo, 'bot_engine_status', 'active');

// Fetch recent 8 users
$recentUsers = $pdo->query("SELECT id, username, email, phone, is_vip, status, created_at FROM users ORDER BY (status = 'pending') DESC, id DESC LIMIT 8")->fetchAll();

// Fetch recent 6 logs
$recentLogs = $pdo->query("SELECT action, details, ip_address, created_at FROM bot_logs ORDER BY id DESC LIMIT 6")->fetchAll();
?>

<?php if (!empty($message)): ?>
    <div class="mb-6 p-4 rounded-sm bg-emerald-500/10 border border-emerald-500/30 text-emerald-400 text-sm flex items-center justify-between">
        <div class="flex items-center gap-2">
            <i class="fa-solid fa-circle-check"></i>
            <span><?= htmlspecialchars($message) ?></span>
        </div>
        <button onclick="this.parentElement.remove()" class="text-slate-400 hover:text-white">&times;</button>
    </div>
<?php endif; ?>

<!-- PENDING APPROVAL ALERT BANNER (IF ANY) -->
<?php if ($pendingUsers > 0): ?>
    <div class="mb-6 p-4 rounded-sm bg-amber-500/15 border border-amber-500/40 text-amber-300 flex flex-col sm:flex-row items-center justify-between gap-4">
        <div class="flex items-center gap-3">
            <div class="w-10 h-10 rounded-sm bg-amber-500/20 text-amber-400 flex items-center justify-center text-lg animate-pulse">
                <i class="fa-solid fa-user-clock"></i>
            </div>
            <div>
                <h4 class="text-sm font-bold text-white"><?= $pendingUsers ?> User(s) Awaiting Admin Approval!</h4>
                <p class="text-xs text-amber-300/80">These users have registered in the app and cannot start bot automation until you approve them.</p>
            </div>
        </div>
        <a href="users.php?status=pending" class="px-5 py-2.5 rounded-sm bg-amber-500 hover:bg-amber-400 text-slate-950 font-bold text-xs shadow-lg transition flex items-center gap-2 whitespace-nowrap">
            <i class="fa-solid fa-check-double"></i> Review &amp; Approve Users
        </a>
    </div>
<?php endif; ?>

<!-- TOP METRICS GRID -->
<div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-5 mb-8">
    <!-- Card 1: Total Users -->
    <div class="glass-card rounded-sm p-5 relative overflow-hidden">
        <div class="flex items-center justify-between">
            <div>
                <p class="text-xs uppercase font-semibold tracking-wider text-slate-400">Total Users</p>
                <h3 class="text-2xl font-black text-white mt-1"><?= number_format($totalUsers) ?></h3>
            </div>
            <div class="w-12 h-12 rounded-sm bg-red-500/15 border border-red-500/30 flex items-center justify-center text-red-400">
                <i class="fa-solid fa-users text-xl"></i>
            </div>
        </div>
        <div class="mt-4 flex items-center text-xs text-slate-400">
            <span class="text-emerald-400 font-semibold mr-1.5"><i class="fa-solid fa-circle-check"></i> <?= $activeUsers ?></span> Active accounts
        </div>
    </div>

    <!-- Card 2: Pending Approvals -->
    <div class="glass-card rounded-sm p-5 relative overflow-hidden border-amber-500/30">
        <div class="flex items-center justify-between">
            <div>
                <p class="text-xs uppercase font-semibold tracking-wider text-amber-400 font-bold">Pending Approvals</p>
                <h3 class="text-2xl font-black text-amber-400 mt-1"><?= number_format($pendingUsers) ?></h3>
            </div>
            <div class="w-12 h-12 rounded-sm bg-amber-500/15 border border-amber-500/30 flex items-center justify-center text-amber-400">
                <i class="fa-solid fa-hourglass-half text-xl"></i>
            </div>
        </div>
        <div class="mt-4 flex items-center text-xs text-slate-400">
            <a href="users.php?status=pending" class="text-amber-400 hover:underline font-semibold">View pending users &rarr;</a>
        </div>
    </div>

    <!-- Card 3: VIP Users -->
    <div class="glass-card rounded-sm p-5 relative overflow-hidden">
        <div class="flex items-center justify-between">
            <div>
                <p class="text-xs uppercase font-semibold tracking-wider text-slate-400">VIP Members</p>
                <h3 class="text-2xl font-black text-amber-400 mt-1"><?= number_format($vipUsers) ?></h3>
            </div>
            <div class="w-12 h-12 rounded-sm bg-amber-500/15 border border-amber-500/30 flex items-center justify-center text-amber-400">
                <i class="fa-solid fa-crown text-xl"></i>
            </div>
        </div>
        <div class="mt-4 flex items-center text-xs text-slate-400">
            <span class="text-cyan-400 font-semibold mr-1.5"><?= $totalUsers > 0 ? round(($vipUsers / $totalUsers) * 100) : 0 ?>%</span> of user base
        </div>
    </div>

    <!-- Card 4: App Global Operational Status -->
    <div class="glass-card rounded-sm p-5 relative overflow-hidden border-l-4 <?= $appStatus === 'active' ? 'border-l-emerald-500' : ($appStatus === 'maintenance' ? 'border-l-amber-500' : 'border-l-red-500') ?>">
        <div class="flex items-center justify-between">
            <div>
                <p class="text-xs uppercase font-semibold tracking-wider text-slate-400">App Status</p>
                <h3 class="text-xl font-black <?= $appStatus === 'active' ? 'text-emerald-400' : ($appStatus === 'maintenance' ? 'text-amber-400' : 'text-red-400') ?> mt-1 uppercase">
                    <?= $appStatus ?>
                </h3>
            </div>
            <div class="w-12 h-12 rounded-sm <?= $appStatus === 'active' ? 'bg-emerald-500/15 border-emerald-500/30 text-emerald-400' : ($appStatus === 'maintenance' ? 'bg-amber-500/15 border-amber-500/30 text-amber-400' : 'bg-red-500/15 border-red-500/30 text-red-400') ?> border flex items-center justify-center">
                <i class="fa-solid fa-power-off text-xl"></i>
            </div>
        </div>
        <div class="mt-4 flex items-center text-xs text-slate-400">
            <a href="settings.php" class="hover:underline text-red-400 font-semibold">Change in Settings &rarr;</a>
        </div>
    </div>
</div>

<!-- QUICK FAST SWITCHES -->
<div class="glass-card rounded-sm p-6 mb-8">
    <h3 class="text-sm font-bold text-white uppercase tracking-wider mb-4 flex items-center gap-2">
        <i class="fa-solid fa-bolt text-amber-400"></i> Fast Control Switches
    </h3>
    <div class="grid grid-cols-1 md:grid-cols-3 gap-4">
        <!-- Quick App Status Switcher -->
        <form method="POST" class="flex items-center justify-between p-4 rounded-sm bg-slate-900/70 border border-slate-800">
            <input type="hidden" name="action" value="set_app_status">
            <div>
                <div class="text-xs font-semibold text-white">App Status</div>
                <div class="text-[11px] text-slate-400">Active / Maint / Off</div>
            </div>
            <div class="flex items-center gap-1.5">
                <button type="submit" name="app_status" value="active" title="Set Active" class="px-2.5 py-1.5 rounded-sm text-xs font-bold transition <?= $appStatus === 'active' ? 'bg-emerald-500 text-slate-950 font-black' : 'bg-slate-800 text-slate-400 hover:text-white' ?>">Active</button>
                <button type="submit" name="app_status" value="maintenance" title="Set Maintenance" class="px-2.5 py-1.5 rounded-sm text-xs font-bold transition <?= $appStatus === 'maintenance' ? 'bg-amber-500 text-slate-950 font-black' : 'bg-slate-800 text-slate-400 hover:text-white' ?>">Maint</button>
                <button type="submit" name="app_status" value="off" title="Set Off" class="px-2.5 py-1.5 rounded-sm text-xs font-bold transition <?= $appStatus === 'off' ? 'bg-red-500 text-white font-black' : 'bg-slate-800 text-slate-400 hover:text-white' ?>">Off</button>
            </div>
        </form>

        <!-- Toggle Admin Approval -->
        <form method="POST" class="flex items-center justify-between p-4 rounded-sm bg-slate-900/70 border border-slate-800">
            <input type="hidden" name="action" value="toggle_approval">
            <div>
                <div class="text-xs font-semibold text-white">Require Approval</div>
                <div class="text-[11px] text-slate-400">New users need approval</div>
            </div>
            <button type="submit" class="px-3.5 py-1.5 rounded-sm text-xs font-bold transition <?= $requireApproval ? 'bg-amber-500 text-slate-950 hover:bg-amber-400' : 'bg-slate-800 text-slate-400 hover:text-white' ?>">
                <?= $requireApproval ? 'Enabled' : 'Disabled' ?>
            </button>
        </form>

        <!-- Toggle Registration -->
        <form method="POST" class="flex items-center justify-between p-4 rounded-sm bg-slate-900/70 border border-slate-800">
            <input type="hidden" name="action" value="toggle_registration">
            <div>
                <div class="text-xs font-semibold text-white">Allow Register</div>
                <div class="text-[11px] text-slate-400">Public app sign-ups</div>
            </div>
            <button type="submit" class="px-3.5 py-1.5 rounded-sm text-xs font-bold transition <?= $regEnabled ? 'bg-red-600 text-white hover:bg-red-500' : 'bg-slate-800 text-slate-400 hover:text-white' ?>">
                <?= $regEnabled ? 'Enabled' : 'Disabled' ?>
            </button>
        </form>
    </div>
</div>

<!-- RECENT USERS TABLE -->
<div class="glass-card rounded-sm p-6 mb-8">
    <div class="flex items-center justify-between mb-4">
        <h3 class="text-sm font-bold text-white uppercase tracking-wider flex items-center gap-2">
            <i class="fa-solid fa-users text-red-400"></i> Recent Registrations
        </h3>
        <a href="users.php" class="text-xs text-red-400 hover:underline">View All Users &rarr;</a>
    </div>

    <div class="overflow-x-auto">
        <table class="w-full text-left text-xs">
            <thead>
                <tr class="border-b border-slate-800 text-slate-400 uppercase tracking-wider">
                    <th class="pb-3 font-semibold">User</th>
                    <th class="pb-3 font-semibold">Phone</th>
                    <th class="pb-3 font-semibold">Email</th>
                    <th class="pb-3 font-semibold">Tier</th>
                    <th class="pb-3 font-semibold">Status</th>
                    <th class="pb-3 font-semibold text-right">Action</th>
                </tr>
            </thead>
            <tbody class="divide-y divide-slate-800/60">
                <?php if (empty($recentUsers)): ?>
                    <tr>
                        <td colspan="6" class="py-6 text-center text-slate-500">No registered users yet.</td>
                    </tr>
                <?php else: ?>
                    <?php foreach ($recentUsers as $ru): ?>
                        <tr class="hover:bg-slate-800/30 transition <?= $ru['status'] === 'pending' ? 'bg-amber-500/5' : '' ?>">
                            <td class="py-3 font-semibold text-white flex items-center gap-2">
                                <div class="w-6 h-6 rounded-full bg-red-500/20 text-red-400 flex items-center justify-center text-[10px] font-bold">
                                    <?= strtoupper(substr($ru['username'], 0, 1)) ?>
                                </div>
                                <span><?= htmlspecialchars($ru['username']) ?></span>
                            </td>
                            <td class="py-3 text-slate-300 font-mono"><?= htmlspecialchars($ru['phone']) ?></td>
                            <td class="py-3 text-slate-300"><?= htmlspecialchars($ru['email']) ?></td>
                            <td class="py-3">
                                <span class="px-2 py-0.5 rounded-full text-[10px] font-bold <?= $ru['is_vip'] == 1 ? 'bg-amber-500/15 text-amber-400 border border-amber-500/30' : 'bg-slate-800 text-slate-400' ?>">
                                    <?= $ru['is_vip'] == 1 ? 'VIP' : 'Regular' ?>
                                </span>
                            </td>
                            <td class="py-3">
                                <span class="px-2 py-0.5 rounded-full text-[10px] font-bold <?= 
                                    $ru['status'] === 'active' ? 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/30' : (
                                    $ru['status'] === 'pending' ? 'bg-amber-500/20 text-amber-400 border border-amber-500/40 animate-pulse' : 
                                    'bg-red-500/10 text-red-400 border border-red-500/30') ?>">
                                    <?= strtoupper($ru['status']) ?>
                                </span>
                            </td>
                            <td class="py-3 text-right">
                                <?php if ($ru['status'] === 'pending'): ?>
                                    <a href="users.php?status=pending" class="px-2.5 py-1 rounded-sm text-[10px] font-bold bg-emerald-500 text-slate-950 hover:bg-emerald-400 transition">
                                        Approve
                                    </a>
                                <?php else: ?>
                                    <a href="users.php?q=<?= urlencode($ru['username']) ?>" class="text-slate-400 hover:text-white">Manage</a>
                                <?php endif; ?>
                            </td>
                        </tr>
                    <?php endforeach; ?>
                <?php endif; ?>
            </tbody>
        </table>
    </div>
</div>

<?php require_once __DIR__ . '/footer.php'; ?>




