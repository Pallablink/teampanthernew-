<?php
require_once __DIR__ . '/../config/db.php';

if (isAdminLoggedIn()) {
    header('Location: dashboard.php');
    exit;
}

$error = '';

if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    $username = trim($_POST['username'] ?? '');
    $password = trim($_POST['password'] ?? '');

    if (empty($username) || empty($password)) {
        $error = 'Please enter both username and password.';
    } else {
        $pdo = getDbConnection();
        try {
            $stmt = $pdo->prepare("SELECT id, username, email, password_hash, role FROM admin_users WHERE username = ? OR email = ? LIMIT 1");
            $stmt->execute([$username, $username]);
            $admin = $stmt->fetch();

            if ($admin && password_verify($password, $admin['password_hash'])) {
                $_SESSION['admin_logged_in'] = true;
                $_SESSION['admin_id'] = $admin['id'];
                $_SESSION['admin_username'] = $admin['username'];
                $_SESSION['admin_role'] = $admin['role'];

                header('Location: dashboard.php');
                exit;
            } else {
                $error = 'Invalid admin credentials. Please try again.';
            }
        } catch (PDOException $e) {
            $error = 'Database error: ' . $e->getMessage();
        }
    }
}
?>
<!DOCTYPE html>
<html lang="en" class="dark">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>TeamPanther - Admin Login</title>
    <script src="https://cdn.tailwindcss.com"></script>
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
    <style>
        body { background-color: #0D0B0E; font-family: 'Inter', system-ui, sans-serif; }
        .glass-card { background-color: #181115; border: 1px solid #2E1E25; }
        .gradient-btn { background: linear-gradient(135deg, #FF3B5C 0%, #EF4444 50%, #B91C1C 100%); }
    </style>
</head>
<body class="min-h-screen flex items-center justify-center p-4">

    <div class="w-full max-w-md">
        <!-- Brand Header -->
        <div class="text-center mb-8">
            <img src="logo_vip_bot.png" alt="TeamPanther" class="w-24 h-24 rounded-sm object-contain shadow-2xl mx-auto mb-4 border border-red-500/30">
            <h1 class="text-2xl font-black tracking-wider text-white">TeamPanther</h1>
            <p class="text-xs text-red-400 font-semibold tracking-widest uppercase mt-1">Administrator Portal</p>
        </div>

        <!-- Login Card -->
        <div class="glass-card rounded-sm p-8 shadow-2xl">
            <div class="mb-6">
                <h2 class="text-lg font-bold text-white">Admin Authentication</h2>
                <p class="text-xs text-slate-400 mt-1">Sign in with your master administrative credentials</p>
            </div>

            <?php if (!empty($error)): ?>
                <div class="mb-5 p-3 rounded-sm bg-red-500/10 border border-red-500/30 text-red-400 text-xs flex items-center gap-2">
                    <i class="fa-solid fa-circle-exclamation text-sm"></i>
                    <span><?= htmlspecialchars($error) ?></span>
                </div>
            <?php endif; ?>

            <form method="POST" action="login.php" class="space-y-5">
                <div>
                    <label class="block text-xs font-semibold text-slate-300 uppercase tracking-wider mb-2">Username or Email</label>
                    <div class="relative">
                        <span class="absolute inset-y-0 left-0 flex items-center pl-3.5 text-slate-400">
                            <i class="fa-solid fa-user-shield text-sm"></i>
                        </span>
                        <input type="text" name="username" required
                                placeholder="e.g. admin"
                                class="w-full pl-10 pr-4 py-3 bg-slate-900/80 border border-slate-700 rounded-sm text-sm text-white placeholder-slate-500 focus:outline-none focus:border-red-500 focus:ring-1 focus:ring-red-500 transition">
                    </div>
                </div>

                <div>
                    <label class="block text-xs font-semibold text-slate-300 uppercase tracking-wider mb-2">Password</label>
                    <div class="relative">
                        <span class="absolute inset-y-0 left-0 flex items-center pl-3.5 text-slate-400">
                            <i class="fa-solid fa-lock text-sm"></i>
                        </span>
                        <input type="password" name="password" required
                                placeholder="••••••••"
                                class="w-full pl-10 pr-4 py-3 bg-slate-900/80 border border-slate-700 rounded-sm text-sm text-white placeholder-slate-500 focus:outline-none focus:border-red-500 focus:ring-1 focus:ring-red-500 transition">
                    </div>
                </div>

                <div class="pt-2">
                    <button type="submit"
                            class="gradient-btn w-full py-3.5 px-4 rounded-sm text-sm font-bold text-white hover:opacity-95 shadow-lg shadow-red-600/30 transition transform active:scale-[0.98]">
                        <i class="fa-solid fa-arrow-right-to-bracket mr-2"></i> Access Admin Dashboard
                    </button>
                </div>
            </form>

            <div class="mt-6 pt-5 border-t border-slate-800 text-center">
                <span class="text-[11px] text-slate-500">
                    Default Credentials: <code class="text-red-400 bg-slate-900 px-1.5 py-0.5 rounded">admin</code> / <code class="text-red-400 bg-slate-900 px-1.5 py-0.5 rounded">admin123</code>
                </span>
            </div>
        </div>
    </div>

</body>
</html>



