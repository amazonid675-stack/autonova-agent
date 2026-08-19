ALTER TABLE `agentTasks` MODIFY COLUMN `status` enum('QUEUED','PLANNING','RUNNING','WAITING_FOR_USER','WAITING_FOR_TOOL','VERIFYING','FAILED','COMPLETED','CANCELLED') NOT NULL DEFAULT 'QUEUED';--> statement-breakpoint
ALTER TABLE `memories` MODIFY COLUMN `layer` enum('SHORT_TERM','TASK','PROJECT','PERSONAL','DOCUMENT') NOT NULL;--> statement-breakpoint
ALTER TABLE `agentTasks` ADD `retryCount` int DEFAULT 0 NOT NULL;--> statement-breakpoint
ALTER TABLE `agentTasks` ADD `nextRetryAt` timestamp;