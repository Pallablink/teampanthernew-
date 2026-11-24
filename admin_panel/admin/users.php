<?php
$pageTitle = 'TeamPanther - User Management';
require_once __DIR__ . '/header.php';

$pdo = getDbConnection();
$feedback = '';

// Handle Actions (Toggle VIP, Status, Delete, Add, Approve)
if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    $action = $_POST['action'] ?? '';
    $userId = (int)($_POST['user_id'] ?? 0);

    if ($action === 'approve_user' && $userId > 0) {
        $stmt = $pdo->prepare("UPDATE users SET status = 'active', is_vip = 1 WHERE id = ?");
        $stmt->execute([$userId]);
        $feedback = "User #$userId approved successfully! Account is now ACTIVE with VIP access.";
        
        $logStmt = $pdo->prepare("INSERT INTO bot_logs (user_id, action, details, ip_address) VALUES (?, 'USER_APPROVED', 'Admin approved pending user account and granted VIP', ?)");
        $logStmt->execute([$userId, $_SERVER['REMOTE_ADDR'] ?? '']);
    } elseif ($action === 'toggle_vip' && $userId > 0) {
        $stmt = $pdo->prepare("UPDATE users SET is_vip = IF(is_vip = 1, 0, 1) WHERE id = ?");
        $stmt->execute([$userId]);
        $feedback = 'User VIP status updated successfully.';
    } elseif ($action === 'change_status' && $userId > 0) {
        $newStatus = $_POST['status'] ?? 'active';
        if (in_array($newStatus, ['pending', 'active', 'inactive', 'banned', 'suspended'])) {
            $stmt = $pdo->prepare("UPDATE users SET status = ? WHERE id = ?");
            $stmt->execute([$newStatus, $userId]);
            $feedback = "User #$userId account status updated to " . strtoupper($newStatus) . ".";
        }
    } elseif ($action === 'delete_user' && $userId > 0) {
        $stmt = $pdo->prepare("DELETE FROM users WHERE id = ?");
        $stmt->execute([$userId]);
        $feedback = 'User deleted successfully.';
    } elseif ($action === 'add_user') {
        $uname = trim($_POST['username'] ?? '');
        $uemail = strtolower(trim($_POST['email'] ?? ''));
        $uphone = trim($_POST['phone'] ?? '');
        $upass = trim($_POST['password'] ?? '');
        $isVip = isset($_POST['is_vip']) ? 1 : 0;
        $status = $_POST['status'] ?? 'active';

        if (!empty($uname) && !empty($uemail) && !empty($uphone) && !empty($upass)) {
            try {
                $hash = password_hash($upass, PASSWORD_BCRYPT);
                $stmt = $pdo->prepare("INSERT INTO users (username, phone, email, password_hash, is_vip, status) VALUES (?, ?, ?, ?, ?, ?)");
                $stmt->execute([$uname, $uphone, $uemail, $hash, $isVip, $status]);
                $feedback = "New user '$uname' added successfully.";
            } catch (PDOException $e) {
                $feedback = 'Failed to add user: ' . $e->getMessage();
            }
        }
    }
}

// Search and filter
$search = trim($_GET['q'] ?? '');
$filterVip = $_GET['vip'] ?? 'all';
$filterStatus = $_GET['status'] ?? 'all';

// Count pending approvals
$countPending = $pdo->query("SELECT COUNT(*) FROM users WHERE status = 'pending'")->fetchColumn();

$sql = "SELECT * FROM users WHERE 1=1";
$params = [];

if (!empty($search)) {
    $sql .= " AND (username LIKE ? OR email LIKE ? OR phone LIKE ?)";
    $params[] = "%$search%";
    $params[] = "%$search%";
    $params[] = "%$search%";
}

if ($filterStatus !== 'all') {
    $sql .= " AND status = ?";
    $params[] = $filterStatus;
}

if ($filterVip === 'vip') {
    $sql .= " AND is_vip = 1";
} elseif ($filterVip === 'regular') {
    $sql .= " AND is_vip = 0";
}

$sql .= " ORDER BY (status = 'pending') DESC, id DESC";
$stmt = $pdo->prepare($sql);
$stmt->execute($params);
$users = $stmt->fetchAll();
?>

<?php if (!empty($feedback)): ?>
    <div class="mb-6 p-4 rounded-sm bg-red-500/10 border border-red-500/30 text-red-300 text-sm flex items-center justify-between">
        <span><i class="fa-solid fa-circle-info mr-2"></i><?= htmlspecialchars($feedback) ?></span>
        <button onclick="this.parentElement.remove()" class="text-slate-400 hover:text-white">&times;</button>
    </div>
<?php endif; ?>

<!-- SEARCH & ACTION BAR -->
<div class="glass-card rounded-sm p-5 mb-6 flex flex-col md:flex-row items-center justify-between gap-4">
    <form method="GET" class="flex flex-wrap items-center gap-3 w-full md:w-auto">
        <div class="relative w-full sm:w-64">
            <span class="absolute inset-y-0 left-0 flex items-center pl-3 text-slate-400">
                <i class="fa-solid fa-magnifying-glass text-xs"></i>
            </span>
            <input type="text" name="q" value="<?= htmlspecialchars($search) ?>" placeholder="Search username, email, phone..."
                   class="w-full pl-9 pr-3 py-2 bg-slate-900 border border-slate-700 rounded-sm text-xs text-white placeholder-slate-500 focus:outline-none focus:border-red-500">
        </div>

        <select name="status" onchange="this.form.submit()" class="py-2 px-3 bg-slate-900 border border-slate-700 rounded-sm text-xs text-white focus:outline-none focus:border-red-500">
            <option value="all" <?= $filterStatus === 'all' ? 'selected' : '' ?>>All Statuses</option>
            <option value="pending" <?= $filterStatus === 'pending' ? 'selected' : '' ?>>⏳ Pending Approval (<?= $countPending ?>)</option>
            <option value="active" <?= $filterStatus === 'active' ? 'selected' : '' ?>>🟢 Active</option>
            <option value="suspended" <?= $filterStatus === 'suspended' ? 'selected' : '' ?>>🟠 Suspended</option>
            <option value="banned" <?= $filterStatus === 'banned' ? 'selected' : '' ?>>🔴 Banned</option>
        </select>

        <select name="vip" onchange="this.form.submit()" class="py-2 px-3 bg-slate-900 border border-slate-700 rounded-sm text-xs text-white focus:outline-none focus:border-red-500">
            <option value="all" <?= $filterVip === 'all' ? 'selected' : '' ?>>All Tiers</option>
            <option value="vip" <?= $filterVip === 'vip' ? 'selected' : '' ?>>VIP Only</option>
            <option value="regular" <?= $filterVip === 'regular' ? 'selected' : '' ?>>Regular Only</option>
        </select>

        <button type="submit" class="px-4 py-2 bg-slate-800 hover:bg-slate-700 rounded-sm text-xs font-semibold text-white transition">Filter</button>
        <?php if (!empty($search) || $filterVip !== 'all' || $filterStatus !== 'all'): ?>
            <a href="users.php" class="text-xs text-slate-400 hover:text-white underline">Clear</a>
        <?php endif; ?>
    </form>

    <div class="flex items-center gap-3">
        <?php if ($countPending > 0): ?>
            <a href="users.php?status=pending" class="px-3.5 py-2 rounded-sm text-xs font-bold bg-amber-500/20 text-amber-400 border border-amber-500/40 animate-pulse flex items-center gap-2">
                <i class="fa-solid fa-clock"></i> <?= $countPending ?> Pending Approval
            </a>
        <?php endif; ?>
        <button onclick="document.getElementById('addUserModal').classList.remove('hidden')"
                class="gradient-btn px-4 py-2.5 rounded-sm text-xs font-bold text-white shadow-lg shadow-red-600/20 hover:opacity-95 transition flex items-center gap-2">
            <i class="fa-solid fa-user-plus"></i> Add New User
        </button>
    </div>
</div>

<!-- USERS TABLE -->
<div class="glass-card rounded-sm p-6 overflow-hidden">
    <div class="overflow-x-auto">
        <table class="w-full text-left text-xs">
            <thead>
                <tr class="border-b border-slate-800 text-slate-400 uppercase tracking-wider">
                    <th class="pb-3 font-semibold">ID</th>
                    <th class="pb-3 font-semibold">User</th>
                    <th class="pb-3 font-semibold">Phone</th>
                    <th class="pb-3 font-semibold">Email</th>
                    <th class="pb-3 font-semibold">VIP Tier</th>
                    <th class="pb-3 font-semibold">Account Status</th>
                    <th class="pb-3 font-semibold">Registered</th>
                    <th class="pb-3 font-semibold text-right">Approval &amp; Actions</th>
                </tr>
            </thead>
            <tbody class="divide-y divide-slate-800/60">
                <?php if (empty($users)): ?>
                    <tr>
                        <td colspan="8" class="py-8 text-center text-slate-500">No users found matching your query.</td>
                    </tr>
                <?php else: ?>
                    <?php foreach ($users as $u): ?>
                        <tr class="hover:bg-slate-800/30 transition <?= $u['status'] === 'pending' ? 'bg-amber-500/5' : '' ?>">
                            <td class="py-3.5 text-slate-500 font-mono">#<?= $u['id'] ?></td>
                            <td class="py-3.5 font-semibold text-white flex items-center gap-2">
                                <div class="w-7 h-7 rounded-full bg-red-500/20 text-red-400 flex items-center justify-center text-[11px] font-bold">
                                    <?= strtoupper(substr($u['username'], 0, 1)) ?>
                                </div>
                                <div>
                                    <div><?= htmlspecialchars($u['username']) ?></div>
                                    <?php if ($u['status'] === 'pending'): ?>
                                        <span class="text-[9px] text-amber-400 font-bold tracking-wider uppercase">Needs Approval</span>
                                    <?php endif; ?>
                                </div>
                            </td>
                            <td class="py-3.5 text-slate-300 font-mono"><?= htmlspecialchars($u['phone']) ?></td>
                            <td class="py-3.5 text-slate-300"><?= htmlspecialchars($u['email']) ?></td>
                            <td class="py-3.5">
                                <form method="POST" class="inline">
                                    <input type="hidden" name="user_id" value="<?= $u['id'] ?>">
                                    <input type="hidden" name="action" value="toggle_vip">
                                    <button type="submit" title="Click to Toggle VIP" class="px-2 py-0.5 rounded-full text-[10px] font-bold cursor-pointer transition <?= $u['is_vip'] == 1 ? 'bg-amber-500/15 text-amber-400 border border-amber-500/30 hover:bg-amber-500/25' : 'bg-slate-800 text-slate-400 hover:text-white' ?>">
                                        <i class="fa-solid fa-crown text-[9px] mr-1"></i> <?= $u['is_vip'] == 1 ? 'VIP Active' : 'Regular' ?>
                                    </button>
                                </form>
                            </td>
                            <td class="py-3.5">
                                <form method="POST" class="inline">
                                    <input type="hidden" name="user_id" value="<?= $u['id'] ?>">
                                    <input type="hidden" name="action" value="change_status">
                                    <select name="status" onchange="this.form.submit()" class="px-2 py-1 rounded-sm text-[10px] font-bold border cursor-pointer focus:outline-none transition <?= 
                                        $u['status'] === 'active' ? 'bg-emerald-500/10 text-emerald-400 border-emerald-500/30' : (
                                        $u['status'] === 'pending' ? 'bg-amber-500/15 text-amber-400 border-amber-500/40 font-black' : (
                                        $u['status'] === 'banned' ? 'bg-red-500/10 text-red-400 border-red-500/30' : (
                                        $u['status'] === 'suspended' ? 'bg-orange-500/10 text-orange-400 border-orange-500/30' : 
                                        'bg-slate-800 text-slate-400 border-slate-700'))) ?>">
                                        <option value="pending" <?= $u['status'] === 'pending' ? 'selected' : '' ?>>⏳ Pending</option>
                                        <option value="active" <?= $u['status'] === 'active' ? 'selected' : '' ?>>🟢 Active</option>
                                        <option value="inactive" <?= $u['status'] === 'inactive' ? 'selected' : '' ?>>⚪ Inactive</option>
                                        <option value="suspended" <?= $u['status'] === 'suspended' ? 'selected' : '' ?>>🟠 Suspended</option>
                                        <option value="banned" <?= $u['status'] === 'banned' ? 'selected' : '' ?>>🔴 Banned</option>
                                    </select>
                                </form>
                            </td>
                            <td class="py-3.5 text-slate-400"><?= date('M d, Y', strtotime($u['created_at'])) ?></td>
                            <td class="py-3.5 text-right space-x-2">
                                <?php if ($u['status'] === 'pending'): ?>
                                    <form method="POST" class="inline">
                                        <input type="hidden" name="user_id" value="<?= $u['id'] ?>">
                                        <input type="hidden" name="action" value="approve_user">
                                        <button type="submit" class="px-3 py-1 rounded-sm text-[11px] font-bold bg-emerald-500 text-slate-950 hover:bg-emerald-400 shadow-md transition">
                                            <i class="fa-solid fa-check mr-1"></i> Approve VIP
                                        </button>
                                    </form>
                                <?php endif; ?>
                                <form method="POST" onsubmit="return confirm('Are you sure you want to permanently delete this user?');" class="inline">
                                    <input type="hidden" name="user_id" value="<?= $u['id'] ?>">
                                    <input type="hidden" name="action" value="delete_user">
                                    <button type="submit" title="Delete User" class="p-1.5 text-slate-400 hover:text-red-400 rounded hover:bg-slate-800 transition">
                                        <i class="fa-solid fa-trash-can"></i>
                                    </button>
                                </form>
                            </td>
                        </tr>
                    <?php endforeach; ?>
                <?php endif; ?>
            </tbody>
        </table>
    </div>
</div>

<!-- ADD USER MODAL -->
<div id="addUserModal" class="hidden fixed inset-0 bg-black/70 backdrop-blur-sm z-50 flex items-center justify-center p-4">
    <div class="glass-card rounded-sm w-full max-w-md p-6 shadow-2xl">
        <div class="flex items-center justify-between pb-4 border-b border-slate-800 mb-4">
            <h3 class="text-sm font-bold text-white uppercase tracking-wider">Create User Account</h3>
            <button onclick="document.getElementById('addUserModal').classList.add('hidden')" class="text-slate-400 hover:text-white">&times;</button>
        </div>

        <form method="POST" class="space-y-4">
            <input type="hidden" name="action" value="add_user">

            <div>
                <label class="block text-xs font-semibold text-slate-300 mb-1">Username</label>
                <input type="text" name="username" required placeholder="e.g. VIP_Trader"
                       class="w-full px-3 py-2 bg-slate-900 border border-slate-700 rounded-sm text-xs text-white focus:outline-none focus:border-red-500">
            </div>

            <div>
                <label class="block text-xs font-semibold text-slate-300 mb-1">Phone Number</label>
                <input type="text" name="phone" required placeholder="e.g. +91 98765 43210"
                       class="w-full px-3 py-2 bg-slate-900 border border-slate-700 rounded-sm text-xs text-white focus:outline-none focus:border-red-500">
            </div>

            <div>
                <label class="block text-xs font-semibold text-slate-300 mb-1">Email Address</label>
                <input type="email" name="email" required placeholder="user@teampanther.com"
                       class="w-full px-3 py-2 bg-slate-900 border border-slate-700 rounded-sm text-xs text-white focus:outline-none focus:border-red-500">
            </div>

            <div>
                <label class="block text-xs font-semibold text-slate-300 mb-1">Password</label>
                <input type="password" name="password" required minlength="6" placeholder="Minimum 6 characters"
                       class="w-full px-3 py-2 bg-slate-900 border border-slate-700 rounded-sm text-xs text-white focus:outline-none focus:border-red-500">
            </div>

            <div class="grid grid-cols-2 gap-3">
                <div>
                    <label class="block text-xs font-semibold text-slate-300 mb-1">Account Status</label>
                    <select name="status" class="w-full px-3 py-2 bg-slate-900 border border-slate-700 rounded-sm text-xs text-white focus:outline-none focus:border-red-500">
                        <option value="active">Active (Approved)</option>
                        <option value="pending">Pending Approval</option>
                        <option value="suspended">Suspended</option>
                    </select>
                </div>
                <div class="flex items-center pt-5">
                    <label class="flex items-center gap-2 cursor-pointer">
                        <input type="checkbox" name="is_vip" value="1" checked class="rounded bg-slate-900 border-slate-700 text-red-600 focus:ring-0">
                        <span class="text-xs font-semibold text-amber-400"><i class="fa-solid fa-crown mr-1"></i> Grant VIP</span>
                    </label>
                </div>
            </div>

            <div class="flex justify-end gap-3 pt-3 border-t border-slate-800">
                <button type="button" onclick="document.getElementById('addUserModal').classList.add('hidden')"
                        class="px-4 py-2 bg-slate-800 hover:bg-slate-700 rounded-sm text-xs text-slate-300 transition">Cancel</button>
                <button type="submit" class="gradient-btn px-5 py-2 rounded-sm text-xs font-bold text-white shadow-lg shadow-red-600/30">
                    Create User
                </button>
            </div>
        </form>
    </div>
</div>

<?php require_once __DIR__ . '/footer.php'; ?>




