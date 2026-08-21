CREATE TABLE `improvementRecords` (
	`id` int AUTO_INCREMENT NOT NULL,
	`userId` int NOT NULL,
	`scope` enum('PROMPT','TOOL','WORKFLOW','MODEL_ROUTING') NOT NULL,
	`title` varchar(200) NOT NULL,
	`proposedChange` text NOT NULL,
	`evidence` text NOT NULL,
	`testOutcome` text,
	`benchmarkSummary` text,
	`versionLabel` varchar(120) NOT NULL,
	`status` enum('PENDING','APPROVED','REJECTED','ROLLED_BACK') NOT NULL DEFAULT 'PENDING',
	`reviewNote` text,
	`approvedAt` timestamp,
	`rolledBackAt` timestamp,
	`createdAt` timestamp NOT NULL DEFAULT (now()),
	`updatedAt` timestamp NOT NULL DEFAULT (now()) ON UPDATE CURRENT_TIMESTAMP,
	CONSTRAINT `improvementRecords_id` PRIMARY KEY(`id`)
);
--> statement-breakpoint
ALTER TABLE `improvementRecords` ADD CONSTRAINT `improvementRecords_userId_users_id_fk` FOREIGN KEY (`userId`) REFERENCES `users`(`id`) ON DELETE cascade ON UPDATE no action;