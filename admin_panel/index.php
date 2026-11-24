<?php
/**
 * Root Index Redirect
 */
require_once __DIR__ . '/config/db.php';

if (isAdminLoggedIn()) {
    header('Location: admin/dashboard.php');
} else {
    header('Location: admin/login.php');
}
exit;


