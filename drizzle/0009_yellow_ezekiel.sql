CREATE TABLE `taskEvidenceRecords` (
	`id` int AUTO_INCREMENT NOT NULL,
	`taskId` int NOT NULL,
	`userId` int NOT NULL,
	`kind` enum('OBSERVATION','TOOL_SELECTION','TOOL_APPROVAL','TOOL_OUTCOME','VERIFICATION','REPAIR','ESCALATION') NOT NULL,
	`toolKey` varchar(80),
	`summary` text NOT NULL,
	`evidence` text,
	`outcome` enum('PENDING','APPROVED','COMPLETED','FAILED','DECLINED') NOT NULL DEFAULT 'PENDING',
	`createdAt` timestamp NOT NULL DEFAULT (now()),
	CONSTRAINT `taskEvidenceRecords_id` PRIMARY KEY(`id`)
);
--> statement-breakpoint
ALTER TABLE `taskEvidenceRecords` ADD CONSTRAINT `taskEvidenceRecords_taskId_agentTasks_id_fk` FOREIGN KEY (`taskId`) REFERENCES `agentTasks`(`id`) ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE `taskEvidenceRecords` ADD CONSTRAINT `taskEvidenceRecords_userId_users_id_fk` FOREIGN KEY (`userId`) REFERENCES `users`(`id`) ON DELETE cascade ON UPDATE no action;