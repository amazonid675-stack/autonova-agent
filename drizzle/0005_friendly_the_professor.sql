CREATE TABLE `capabilityGrants` (
	`id` int AUTO_INCREMENT NOT NULL,
	`userId` int NOT NULL,
	`capability` varchar(100) NOT NULL,
	`scope` text NOT NULL,
	`rationale` text,
	`status` enum('PENDING','APPROVED','DECLINED','REVOKED') NOT NULL DEFAULT 'PENDING',
	`expiresAt` timestamp,
	`createdAt` timestamp NOT NULL DEFAULT (now()),
	`updatedAt` timestamp NOT NULL DEFAULT (now()) ON UPDATE CURRENT_TIMESTAMP,
	CONSTRAINT `capabilityGrants_id` PRIMARY KEY(`id`)
);
--> statement-breakpoint
CREATE TABLE `githubConnections` (
	`id` int AUTO_INCREMENT NOT NULL,
	`userId` int NOT NULL,
	`login` varchar(160) NOT NULL,
	`encryptedToken` text NOT NULL,
	`scopes` varchar(1000) NOT NULL,
	`createdAt` timestamp NOT NULL DEFAULT (now()),
	`updatedAt` timestamp NOT NULL DEFAULT (now()) ON UPDATE CURRENT_TIMESTAMP,
	CONSTRAINT `githubConnections_id` PRIMARY KEY(`id`),
	CONSTRAINT `github_connections_user_unique` UNIQUE(`userId`)
);
--> statement-breakpoint
CREATE TABLE `githubOperationRequests` (
	`id` int AUTO_INCREMENT NOT NULL,
	`userId` int NOT NULL,
	`repository` varchar(255) NOT NULL,
	`operation` enum('CREATE_ISSUE','CREATE_BRANCH','CREATE_PULL_REQUEST') NOT NULL,
	`payload` text NOT NULL,
	`status` enum('PENDING','APPROVED','COMPLETED','FAILED','CANCELLED') NOT NULL DEFAULT 'PENDING',
	`resultSummary` text,
	`errorSummary` text,
	`createdAt` timestamp NOT NULL DEFAULT (now()),
	`updatedAt` timestamp NOT NULL DEFAULT (now()) ON UPDATE CURRENT_TIMESTAMP,
	CONSTRAINT `githubOperationRequests_id` PRIMARY KEY(`id`)
);
--> statement-breakpoint
CREATE TABLE `learningCandidates` (
	`id` int AUTO_INCREMENT NOT NULL,
	`userId` int NOT NULL,
	`title` varchar(200) NOT NULL,
	`content` text NOT NULL,
	`layer` enum('TASK','PROJECT','PERSONAL','DOCUMENT') NOT NULL DEFAULT 'PERSONAL',
	`source` varchar(160) NOT NULL DEFAULT 'ANDROID_LOCAL',
	`status` enum('PENDING','APPROVED','DISMISSED') NOT NULL DEFAULT 'PENDING',
	`memoryId` int,
	`createdAt` timestamp NOT NULL DEFAULT (now()),
	`updatedAt` timestamp NOT NULL DEFAULT (now()) ON UPDATE CURRENT_TIMESTAMP,
	CONSTRAINT `learningCandidates_id` PRIMARY KEY(`id`)
);
--> statement-breakpoint
CREATE TABLE `researchSessions` (
	`id` int AUTO_INCREMENT NOT NULL,
	`userId` int NOT NULL,
	`query` varchar(600) NOT NULL,
	`status` enum('DRAFT','RUNNING','COMPLETED','FAILED') NOT NULL DEFAULT 'DRAFT',
	`summary` text,
	`errorSummary` text,
	`createdAt` timestamp NOT NULL DEFAULT (now()),
	`updatedAt` timestamp NOT NULL DEFAULT (now()) ON UPDATE CURRENT_TIMESTAMP,
	CONSTRAINT `researchSessions_id` PRIMARY KEY(`id`)
);
--> statement-breakpoint
CREATE TABLE `researchSources` (
	`id` int AUTO_INCREMENT NOT NULL,
	`sessionId` int NOT NULL,
	`userId` int NOT NULL,
	`sourceUrl` varchar(2048) NOT NULL,
	`host` varchar(255) NOT NULL,
	`title` varchar(500),
	`excerpt` text,
	`citationLabel` varchar(32) NOT NULL,
	`fetchStatus` enum('PENDING','FETCHED','REJECTED','FAILED') NOT NULL DEFAULT 'PENDING',
	`createdAt` timestamp NOT NULL DEFAULT (now()),
	CONSTRAINT `researchSources_id` PRIMARY KEY(`id`)
);
--> statement-breakpoint
ALTER TABLE `capabilityGrants` ADD CONSTRAINT `capabilityGrants_userId_users_id_fk` FOREIGN KEY (`userId`) REFERENCES `users`(`id`) ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE `githubConnections` ADD CONSTRAINT `githubConnections_userId_users_id_fk` FOREIGN KEY (`userId`) REFERENCES `users`(`id`) ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE `githubOperationRequests` ADD CONSTRAINT `githubOperationRequests_userId_users_id_fk` FOREIGN KEY (`userId`) REFERENCES `users`(`id`) ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE `learningCandidates` ADD CONSTRAINT `learningCandidates_userId_users_id_fk` FOREIGN KEY (`userId`) REFERENCES `users`(`id`) ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE `learningCandidates` ADD CONSTRAINT `learningCandidates_memoryId_memories_id_fk` FOREIGN KEY (`memoryId`) REFERENCES `memories`(`id`) ON DELETE set null ON UPDATE no action;--> statement-breakpoint
ALTER TABLE `researchSessions` ADD CONSTRAINT `researchSessions_userId_users_id_fk` FOREIGN KEY (`userId`) REFERENCES `users`(`id`) ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE `researchSources` ADD CONSTRAINT `researchSources_sessionId_researchSessions_id_fk` FOREIGN KEY (`sessionId`) REFERENCES `researchSessions`(`id`) ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE `researchSources` ADD CONSTRAINT `researchSources_userId_users_id_fk` FOREIGN KEY (`userId`) REFERENCES `users`(`id`) ON DELETE cascade ON UPDATE no action;