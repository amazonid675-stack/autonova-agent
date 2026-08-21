ALTER TABLE `githubOperationRequests` ADD `capabilityGrantId` int;--> statement-breakpoint
ALTER TABLE `researchSessions` ADD `capabilityGrantId` int;--> statement-breakpoint
ALTER TABLE `githubOperationRequests` ADD `capabilityGrantId` int;--> statement-breakpoint
ALTER TABLE `githubOperationRequests` ADD CONSTRAINT `githubOperationRequests_capabilityGrantId_capabilityGrants_id_fk` FOREIGN KEY (`capabilityGrantId`) REFERENCES `capabilityGrants`(`id`) ON DELETE set null ON UPDATE no action;--> statement-breakpoint
ALTER TABLE `researchSessions` ADD CONSTRAINT `researchSessions_capabilityGrantId_capabilityGrants_id_fk` FOREIGN KEY (`capabilityGrantId`) REFERENCES `capabilityGrants`(`id`) ON DELETE set null ON UPDATE no action;
