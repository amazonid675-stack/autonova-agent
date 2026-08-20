CREATE TABLE `mobileAuthGrants` (
	`id` int AUTO_INCREMENT NOT NULL,
	`userId` int NOT NULL,
	`codeHash` varchar(64) NOT NULL,
	`verifierHash` varchar(64) NOT NULL,
	`encryptedSessionToken` text NOT NULL,
	`expiresAt` timestamp NOT NULL,
	`consumedAt` timestamp,
	`createdAt` timestamp NOT NULL DEFAULT (now()),
	CONSTRAINT `mobileAuthGrants_id` PRIMARY KEY(`id`),
	CONSTRAINT `mobile_auth_grants_code_hash_unique` UNIQUE(`codeHash`)
);
--> statement-breakpoint
ALTER TABLE `mobileAuthGrants` ADD CONSTRAINT `mobileAuthGrants_userId_users_id_fk` FOREIGN KEY (`userId`) REFERENCES `users`(`id`) ON DELETE cascade ON UPDATE no action;