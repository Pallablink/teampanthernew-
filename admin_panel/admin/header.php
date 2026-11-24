<?php
require_once __DIR__ . '/../config/db.php';
requireAdminLogin();

$currentPage = basename($_SERVER['PHP_SELF'], '.php');
$adminUsername = $_SESSION['admin_username'] ?? 'Administrator';
?>
<!DOCTYPE html>
<html lang="en" class="dark">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title><?= htmlspecialchars($pageTitle ?? 'TeamPanther Dashboard') ?></title>
    <!-- Tailwind CSS CDN -->
    <script src="https://cdn.tailwindcss.com"></script>
    <script>
        tailwind.config = {
            darkMode: 'class',
            theme: {
                extend: {
                    colors: {
                        vipDark: '#0D0B0E',
                        vipCard: '#181115',
                        vipBorder: '#2E1E25',
                        vipPrimary: '#EF4444',
                        vipPrimaryDark: '#B91C1C',
                        vipCyan: '#FF3B5C',
                        vipGold: '#F59E0B'
                    }
                }
            }
        }
    </script>
    <!-- Font Awesome CDN -->
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
    <style>
        body { background-color: #0D0B0E; font-family: 'Inter', system-ui, -apple-system, sans-serif; }
        .glass-card { background-color: #181115; border: 1px solid #2E1E25; }
        .gradient-btn { background: linear-gradient(135deg, #FF3B5C 0%, #EF4444 50%, #B91C1C 100%); }
        .custom-scroll::-webkit-scrollbar { width: 6px; height: 6px; }
        .custom-scroll::-webkit-scrollbar-track { background: #0D0B0E; }
        .custom-scroll::-webkit-scrollbar-thumb { background: #2E1E25; border-radius: 4px; }
    </style>
</head>
<body class="text-slate-100 min-h-screen flex flex-col md:flex-row antialiased custom-scroll">

    <!-- SIDEBAR -->
    <aside class="w-full md:w-64 glass-card md:min-h-screen flex flex-col justify-between border-r border-slate-800 p-5 shrink-0">
        <div>
            <!-- Brand Logo -->
            <div class="flex items-center space-x-3 pb-6 border-b border-slate-800">
                <img src="logo_vip_bot.png" alt="TeamPanther" class="w-10 h-10 rounded-sm object-contain shadow-lg shadow-red-500/20">
                <div>
                    <h1 class="font-extrabold text-base tracking-wider bg-gradient-to-r from-white via-red-200 to-rose-400 bg-clip-text text-transparent">TeamPanther</h1>
                    <span class="text-[10px] text-red-400 font-semibold uppercase tracking-widest">Admin Control</span>
                </div>
            </div>

            <!-- Navigation Links -->
            <nav class="mt-6 space-y-1.5">
                <a href="dashboard.php" class="flex items-center space-x-3 px-4 py-3 rounded-sm text-sm font-medium transition-all <?= $currentPage === 'dashboard' ? 'bg-red-600/20 text-red-400 border border-red-500/30' : 'text-slate-400 hover:text-white hover:bg-slate-800/60' ?>">
                    <i class="fa-solid fa-gauge-high w-5"></i>
                    <span>Dashboard</span>
                </a>

                <a href="users.php" class="flex items-center space-x-3 px-4 py-3 rounded-sm text-sm font-medium transition-all <?= $currentPage === 'users' ? 'bg-red-600/20 text-red-400 border border-red-500/30' : 'text-slate-400 hover:text-white hover:bg-slate-800/60' ?>">
                    <i class="fa-solid fa-users w-5"></i>
                    <span>User Management</span>
                </a>

                <a href="settings.php" class="flex items-center space-x-3 px-4 py-3 rounded-sm text-sm font-medium transition-all <?= $currentPage === 'settings' ? 'bg-red-600/20 text-red-400 border border-red-500/30' : 'text-slate-400 hover:text-white hover:bg-slate-800/60' ?>">
                    <i class="fa-solid fa-sliders w-5"></i>
                    <span>App Settings</span>
                </a>

                <a href="../api/get_settings.php" target="_blank" class="flex items-center space-x-3 px-4 py-3 rounded-sm text-sm font-medium text-slate-400 hover:text-white hover:bg-slate-800/60 transition-all">
                    <i class="fa-solid fa-code w-5"></i>
                    <span>View Public API</span>
                    <i class="fa-solid fa-arrow-up-right-from-square text-xs ml-auto opacity-60"></i>
                </a>
            </nav>
        </div>

        <!-- Admin Profile & Logout -->
        <div class="pt-6 border-t border-slate-800 mt-6 md:mt-0">
            <div class="flex items-center justify-between">
                <div class="flex items-center space-x-3">
                    <div class="w-9 h-9 rounded-full bg-slate-800 border border-slate-700 flex items-center justify-center text-slate-300">
                        <i class="fa-solid fa-user-shield text-sm"></i>
                    </div>
                    <div class="truncate">
                        <div class="text-xs font-semibold text-white truncate"><?= htmlspecialchars($adminUsername) ?></div>
                        <div class="text-[10px] text-emerald-400 flex items-center gap-1">
                            <span class="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-pulse"></span> Online
                        </div>
                    </div>
                </div>
                <a href="logout.php" title="Logout" class="p-2 text-slate-400 hover:text-red-400 rounded-sm hover:bg-slate-800 transition">
                    <i class="fa-solid fa-right-from-bracket"></i>
                </a>
            </div>
        </div>
    </aside>

    <!-- MAIN CONTENT AREA -->
    <div class="flex-1 flex flex-col min-w-0">
        <!-- Top bar -->
        <header class="glass-card border-b border-slate-800 px-6 py-4 flex items-center justify-between">
            <div class="flex items-center space-x-3">
                <h2 class="text-lg font-bold text-white"><?= htmlspecialchars($pageTitle ?? 'Overview') ?></h2>
            </div>
            <div class="flex items-center space-x-4">
                <span class="px-3 py-1 rounded-full text-xs font-medium bg-red-500/10 text-red-400 border border-red-500/20">
                    <i class="fa-solid fa-shield-halved mr-1.5"></i> cPanel Ready
                </span>
                <span class="text-xs text-slate-400 hidden sm:inline"><?= date('D, M d, Y') ?></span>
            </div>
        </header>

        <!-- Content -->
        <main class="p-6 flex-1">



