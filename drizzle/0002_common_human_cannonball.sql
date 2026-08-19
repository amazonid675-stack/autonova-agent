CREATE TABLE `mobileDevices` (
	`id` int AUTO_INCREMENT NOT NULL,
	`userId` int NOT NULL,
	`deviceId` varchar(128) NOT NULL,
	`label` varchar(160) NOT NULL,
	`platform` enum('ANDROID') NOT NULL DEFAULT 'ANDROID',
	`pushEnabled` int NOT NULL DEFAULT 0,
	`lastSyncedAt` timestamp,
	`createdAt` timestamp NOT NULL DEFAULT (now()),
	`updatedAt` timestamp NOT NULL DEFAULT (now()) ON UPDATE CURRENT_TIMESTAMP,
	CONSTRAINT `mobileDevices_id` PRIMARY KEY(`id`),
	CONSTRAINT `mobile_devices_user_device_unique` UNIQUE(`userId`,`deviceId`)
);
--> statement-breakpoint
ALTER TABLE `mobileDevices` ADD CONSTRAINT `mobileDevices_userId_users_id_fk` FOREIGN KEY (`userId`) REFERENCES `users`(`id`) ON DELETE cascade ON UPDATE no action;