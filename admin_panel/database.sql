-- Database Schema for TeamPanther Admin Panel & App Backend
-- Target Backend: https://team-panther.l.cd/
-- Import this SQL file into your cPanel phpMyAdmin

SET SQL_MODE = "NO_AUTO_VALUE_ON_ZERO";
START TRANSACTION;
SET time_zone = "+00:00";

-- Table: admin_users
CREATE TABLE IF NOT EXISTS `admin_users` (
  `id` INT AUTO_INCREMENT PRIMARY KEY,
  `username` VARCHAR(50) NOT NULL UNIQUE,
  `email` VARCHAR(100) NOT NULL UNIQUE,
  `password_hash` VARCHAR(255) NOT NULL,
  `role` VARCHAR(20) DEFAULT 'superadmin',
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Table: users (App Users)
-- Status: 'pending' (Default - requires Admin Approval), 'active', 'inactive', 'banned', 'suspended'
CREATE TABLE IF NOT EXISTS `users` (
  `id` INT AUTO_INCREMENT PRIMARY KEY,
  `username` VARCHAR(60) NOT NULL UNIQUE,
  `phone` VARCHAR(20) NOT NULL UNIQUE,
  `email` VARCHAR(100) NOT NULL UNIQUE,
  `password_hash` VARCHAR(255) NOT NULL,
  `avatar` VARCHAR(255) DEFAULT 'default_robot',
  `is_vip` TINYINT(1) DEFAULT 0,
  `status` ENUM('pending', 'active', 'inactive', 'banned', 'suspended') DEFAULT 'pending',
  `last_login` DATETIME NULL,
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Table: user_feedback
CREATE TABLE IF NOT EXISTS `user_feedback` (
  `id` INT AUTO_INCREMENT PRIMARY KEY,
  `user_id` INT NULL,
  `phone` VARCHAR(20) NULL,
  `email` VARCHAR(100) NULL,
  `rating` INT DEFAULT 5,
  `message` TEXT NOT NULL,
  `status` ENUM('unread', 'read', 'resolved') DEFAULT 'unread',
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Table: app_settings (Key-Value Dynamic Configuration)
CREATE TABLE IF NOT EXISTS `app_settings` (
  `id` INT AUTO_INCREMENT PRIMARY KEY,
  `setting_key` VARCHAR(100) NOT NULL UNIQUE,
  `setting_value` TEXT NULL,
  `setting_group` VARCHAR(50) DEFAULT 'general',
  `description` VARCHAR(255) NULL,
  `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Table: password_resets
CREATE TABLE IF NOT EXISTS `password_resets` (
  `id` INT AUTO_INCREMENT PRIMARY KEY,
  `email_or_phone` VARCHAR(100) NOT NULL,
  `token` VARCHAR(100) NOT NULL,
  `expires_at` DATETIME NOT NULL,
  `is_used` TINYINT(1) DEFAULT 0,
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Table: bot_logs (Logs for bot actions & events)
CREATE TABLE IF NOT EXISTS `bot_logs` (
  `id` INT AUTO_INCREMENT PRIMARY KEY,
  `user_id` INT NULL,
  `action` VARCHAR(100) NOT NULL,
  `details` TEXT NULL,
  `ip_address` VARCHAR(45) NULL,
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Default Admin User: admin / admin123 (Change password upon first login!)
INSERT INTO `admin_users` (`username`, `email`, `password_hash`, `role`) 
VALUES ('admin', 'admin@teampanther.com', '$2y$10$eE0m7aGgG8Yc9wUe8q0c9.c7n7P0rP2n5T.jLzQ3bO8h4qZz8aK6y', 'superadmin')
ON DUPLICATE KEY UPDATE `id`=`id`;

-- Default App Settings: Includes App Status (active/off/maintenance) and require_admin_approval
INSERT INTO `app_settings` (`setting_key`, `setting_value`, `setting_group`, `description`) VALUES
('app_name', 'TeamPanther', 'general', 'Application Title'),
('app_version', '1.0.0', 'general', 'Current App Version'),
('target_web_url', 'https://kwg08.com/', 'game', 'Target Platform / Game URL to open in app'),
('app_status', 'active', 'general', 'App Operational Status: active, maintenance, or off'),
('maintenance_message', 'TeamPanther is undergoing scheduled system upgrades. Bot automation is temporarily paused.', 'general', 'Maintenance Announcement'),
('require_admin_approval', '1', 'auth', 'Require Admin Approval on user registration before bot starts (1 = Yes, 0 = No)'),
('registration_enabled', '1', 'auth', 'Allow new user registrations (1 = Yes, 0 = No)'),
('vip_auto_activation', '0', 'bot', 'Automatically grant VIP status on registration'),
('bot_engine_status', 'active', 'bot', 'Global Bot Engine Status (active/paused/stopped)'),
('support_contact', 'support@teampanther.com', 'support', 'Official Support Contact')
ON DUPLICATE KEY UPDATE `setting_value`=VALUES(`setting_value`);

COMMIT;


