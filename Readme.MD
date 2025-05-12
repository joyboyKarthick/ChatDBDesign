# Zoho Cliq Chat Data Archival Project

This project provides a database schema design for handling massive chat data in MySQL, along with a Java-based tool to archive historical messages using database partitioning and the `EXCHANGE PARTITION` mechanism.

## Table of Contents

1.  [Problem Description](#problem-description)
2.  [Solution Overview](#solution-overview)
3.  [Database Schema](#database-schema)
4.  [Archiving Tool](#archiving-tool)
    * [How it Works](#how-it-works)
5.  [Data Consistency and Integrity](#data-consistency-and-integrity)
6.  [Searching Archived Data](#searching-archived-data)
7.  [Setup and Usage](#setup-and-usage)
    * [Prerequisites](#prerequisites)
    * [Setup Steps](#setup-steps)
    * [Archiving a Partition](#archiving-a-partition)
    * [Example Archival Execution Flow](Example-archival-Execution-Flow)
8.  [Improvements Needed](#improvements-needed)

## Problem Description

Managing a large volume of chat messages, potentially reaching hundreds of millions or billions of records, presents significant performance and management challenges in a single database table. While recent messages are frequently accessed, older messages are typically queried less often but must remain available. This project aims to tackle this by implementing a strategy to archive historical chat data, keeping the primary `messages` table lean while ensuring older data is accessible from dedicated archive tables.

The design must support core chat functionalities including:

* Mapping between users and chats.
* Maintaining unread message counts for users in chats.
* Storing user-specific chat preferences (like mute status).
* Quick access to the last message details of a chat.
* An efficient mechanism to archive data based on a range (specifically time).
* The ability to search and retrieve messages from archived data.
* Ensuring data consistency throughout the archival process.

## Solution Overview

The proposed solution leverages MySQL's native table partitioning feature on the `messages` table, specifically partitioning by `created_at` timestamp. A custom Java application, `PartitionArchiver`, is developed to perform the archival process using the highly efficient `ALTER TABLE ... EXCHANGE PARTITION` command. Metadata about the archived partitions (which data is in which archive table) is stored in a separate table, `archived_message_partitions`, to enable effective searching across historical data.

The database schema is structured to separate frequently accessed metadata (users, chats, participant info, user-specific chat settings) from the high-volume message data, which is the primary target for archival.

## Database Schema

The database schema is defined in the `schema_and_dummy_data_script.sql` file. Below is a description of each table:

### `users`

Stores core user information.

| Column          | Type                 | Constraints                      | Description                       |
| :-------------- | :------------------- | :------------------------------- | :-------------------------------- |
| `user_id`       | BIGINT UNSIGNED      | PRIMARY KEY, AUTO_INCREMENT      | Unique identifier for the user.   |
| `username`      | VARCHAR(100)         | NOT NULL, UNIQUE                 | User's unique username.           |
| `password_hash` | VARCHAR(255)         | NOT NULL                         | Hashed password.                  |
| `email`         | VARCHAR(255)         | NULL, UNIQUE                     | User's email address.             |
| `full_name`     | VARCHAR(255)         | NULL                             | User's full name.                 |
| `avatar_url`    | VARCHAR(1024)        | NULL                             | URL to the user's avatar image.   |
| `created_at`    | TIMESTAMP            | DEFAULT CURRENT_TIMESTAMP        | Timestamp of user creation.       |
| `updated_at`    | TIMESTAMP            | DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP | Timestamp of last update.         |
| `is_active`     | BOOLEAN              | DEFAULT TRUE                     | Indicates if the user is active.  |

### `chats`

Stores metadata for direct messages and group chats. Includes denormalized `last_message_*` fields for performance.

| Column                       | Type                | Constraints                                                                | Description                                       |
| :--------------------------- | :------------------ | :------------------------------------------------------------------------- | :------------------------------------------------ |
| `chat_id`                    | BIGINT UNSIGNED     | PRIMARY KEY, AUTO_INCREMENT                                                | Unique identifier for the chat.                   |
| `chat_type`                  | ENUM('direct', 'group') | NOT NULL                                                                   | Type of chat (direct message or group).           |
| `group_name`                 | VARCHAR(255)        | NULL                                                                       | Name of the group chat (NULL for direct).         |
| `group_avatar_url`           | VARCHAR(1024)       | NULL                                                                       | URL to the group avatar (NULL for direct).        |
| `created_by_user_id`         | BIGINT UNSIGNED     | NULL, FOREIGN KEY (`users.user_id`) ON DELETE SET NULL                     | User who created the chat.                        |
| `created_at`                 | TIMESTAMP           | DEFAULT CURRENT_TIMESTAMP                                                  | Timestamp of chat creation.                       |
| `updated_at`                 | TIMESTAMP           | DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP                      | Timestamp of last update to chat metadata.        |
| `last_message_id`            | BIGINT UNSIGNED     | NULL                                                                       | ID of the last message in this chat (denormalized). |
| `last_message_sender_id`     | BIGINT UNSIGNED     | NULL, FOREIGN KEY (`users.user_id`) ON DELETE SET NULL                     | Sender ID of the last message (denormalized).     |
| `last_message_content_preview` | VARCHAR(500)        | NULL                                                                       | Preview of the last message content (denormalized). |
| `last_message_created_at`    | TIMESTAMP(6)        | NULL                                                                       | Creation timestamp of the last message (denormalized). |

### `chat_participants`

Links users to chats and defines their role.

| Column              | Type                | Constraints                                                                | Description                           |
| :------------------ | :------------------ | :------------------------------------------------------------------------- | :------------------------------------ |
| `chat_participant_id` | BIGINT UNSIGNED     | PRIMARY KEY, AUTO_INCREMENT                                                | Unique identifier for the participant link. |
| `chat_id`           | BIGINT UNSIGNED     | NOT NULL, FOREIGN KEY (`chats.chat_id`) ON DELETE CASCADE                  | The chat involved.                    |
| `user_id`           | BIGINT UNSIGNED     | NOT NULL, FOREIGN KEY (`users.user_id`) ON DELETE CASCADE                  | The user involved.                    |
| `role`              | ENUM('member', 'admin', 'owner') | DEFAULT 'member'                                                           | User's role in the chat.              |
| `joined_at`         | TIMESTAMP           | DEFAULT CURRENT_TIMESTAMP                                                  | Timestamp when the user joined.       |
| `added_by_user_id`  | BIGINT UNSIGNED     | NULL, FOREIGN KEY (`users.user_id`) ON DELETE SET NULL                     | User who added this participant.      |
| `uk_chat_user`      |                     | UNIQUE KEY (`chat_id`, `user_id`)                                          | Ensures unique user-chat participation. |

### `messages`

Stores the chat messages. This table is partitioned by `created_at`.

| Column                 | Type                | Constraints                                  | Description                                   |
| :--------------------- | :------------------ | :------------------------------------------- | :-------------------------------------------- |
| `message_id`           | BIGINT UNSIGNED     | AUTO_INCREMENT                               | Unique identifier for the message.            |
| `chat_id`              | BIGINT UNSIGNED     | NOT NULL                                     | The chat the message belongs to.              |
| `sender_id`            | BIGINT UNSIGNED     | NOT NULL                                     | User who sent the message.                    |
| `message_type`         | ENUM                | DEFAULT 'text'                               | Type of message content.                      |
| `content`              | TEXT                | NULL                                         | Message text content.                         |
| `media_url`            | VARCHAR(1024)       | NULL                                         | URL for media attachments.                    |
| `thumbnail_url`        | VARCHAR(1024)       | NULL                                         | URL for media thumbnail.                      |
| `file_metadata`        | JSON                | NULL                                         | Metadata for file attachments.                |
| `parent_message_id`    | BIGINT UNSIGNED     | NULL                                         | ID of the message this is a reply to.         |
| `parent_message_preview` | VARCHAR(100)        | NULL                                         | Preview of the parent message.                |
| `created_at`           | DATETIME(6)         | DEFAULT CURRENT_TIMESTAMP(6)                 | Timestamp when the message was created. Used for partitioning. |
| `updated_at`           | DATETIME(6)         | DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) | Timestamp of last message update.           |
| `deleted_at`           | DATETIME            | NULL                                         | Timestamp if the message was deleted.         |
| `PRIMARY KEY`          |                     | (`message_id`, `created_at`)                 | Composite primary key.                        |
| `PARTITIONING`         |                     | `BY RANGE (TO_DAYS(created_at))`             | Partitions based on the day of message creation. |
| `INDEX`                |                     | `idx_chat_created_at (chat_id, created_at DESC)` | Index for fetching messages in a chat.        |
| `INDEX`                |                     | `idx_created_at (created_at)`                | Index on creation time.                       |
| `INDEX`                |                     | `idx_sender_id (sender_id)`                  | Index on sender.                              |
| `INDEX`                |                     | `idx_message_type (message_type)`            | Index on message type.                        |

### `archived_message_partitions`

Stores metadata about the archived `messages` partitions, acting as a registry for historical data.

| Column               | Type                | Constraints         | Description                                       |
| :------------------- | :------------------ | :------------------ | :------------------------------------------------ |
| `archive_table_name` | VARCHAR(255)        | NOT NULL PRIMARY KEY | Name of the archive table containing the data.    |
| `start_timestamp`    | DATETIME(3)         | NOT NULL            | Earliest timestamp of messages in this archive.   |
| `end_timestamp`      | DATETIME(3)         | NOT NULL            | Latest timestamp of messages in this archive.     |
| `min_message_id`     | BIGINT UNSIGNED     | NOT NULL            | Minimum `message_id` in this archive.           |
| `max_message_id`     | BIGINT UNSIGNED     | NOT NULL            | Maximum `message_id` in this archive.           |
| `archived_at`        | DATETIME(3)         | NOT NULL            | Timestamp when this partition was archived.       |
| `INDEX`              |                     | `idx_date_range (start_timestamp, end_timestamp)` | Index for searching archives by date range. |
| `INDEX`              |                     | `idx_message_id_range (min_message_id, max_message_id)` | Optional index for searching by ID range. |

### `user_chat_metadata`

Stores user-specific state and settings for each chat they are a participant in.

| Column                         | Type                | Constraints                                                              | Description                                       |
| :----------------------------- | :------------------ | :----------------------------------------------------------------------- | :------------------------------------------------ |
| `user_chat_metadata_id`        | BIGINT UNSIGNED     | PRIMARY KEY, AUTO_INCREMENT                                              | Unique identifier for the metadata entry.       |
| `user_id`                      | BIGINT UNSIGNED     | NOT NULL, FOREIGN KEY (`users.user_id`) ON DELETE CASCADE              | The user the metadata belongs to.                 |
| `chat_id`                      | BIGINT UNSIGNED     | NOT NULL, FOREIGN KEY (`chats.chat_id`) ON DELETE CASCADE              | The chat the metadata is for.                     |
| `last_read_at`                 | TIMESTAMP(6)        | NULL                                                                     | Timestamp of the last message read by the user.   |
| `unread_count`                 | INT UNSIGNED        | DEFAULT 0                                                                | Number of unread messages for the user in the chat. |
| `is_muted`                     | BOOLEAN             | DEFAULT FALSE                                                            | Indicates if the user has muted the chat.         |
| `is_pinned`                    | BOOLEAN             | DEFAULT FALSE                                                            | Indicates if the user has pinned the chat.        |
| `is_archived_by_user`          | BOOLEAN             | DEFAULT FALSE                                                            | Indicates if the user has archived the chat (from their view). |
| `hide_chat_until_new_message`  | BOOLEAN             | DEFAULT FALSE                                                            | Hides chat until a new message arrives.           |
| `custom_notification_settings` | JSON                | NULL                                                                     | User-specific notification settings for this chat. |
| `last_cleared_at`              | TIMESTAMP(6)        | NULL                                                                     | Timestamp of when the user last cleared chat history (client-side concept). |
| `uk_user_chat`                 |                     | UNIQUE KEY (`user_id`, `chat_id`)                                        | Ensures unique metadata entry per user-chat pair. |

## Archiving Tool

The archiving tool is a Java application designed to leverage MySQL's `EXCHANGE PARTITION` functionality for efficient data archival.

### How it Works

The `PartitionArchiver` class contains the core logic for archiving a specific partition from the `messages` table. The process is performed within a database transaction:

1.  **Identify Target**: The tool is configured with the name of the `messages` table, the `archived_message_partitions` table, and a function to derive the name of the archive table from the partition name (e.g., `p202501` -> `messages_archive_202501`).
2.  **Verify Partition**: It checks if the partition to be archived actually exists in the `messages` table.
3.  **Prepare Archive Table**: It checks if a standalone table with the derived archive name exists. If not, it creates one using `CREATE TABLE ... LIKE messages` to ensure the schema is identical, and then removes any partitioning from this new table using `ALTER TABLE ... REMOVE PARTITIONING`.
4.  **Exchange Data**: The key step is executing `ALTER TABLE messages EXCHANGE PARTITION partitionName WITH TABLE archiveTableName`. This command instantaneously swaps the data files between the partition and the standalone table. The partition in `messages` becomes empty, and the archive table now holds the data that was previously in the partition.
5.  **Record Metadata**: It queries the newly populated archive table to determine the range of `message_id` and `created_at` timestamps it contains. This metadata, along with the archive table name and the archival timestamp, is inserted into the `archived_message_partitions` table.
6.  **Drop Original Partition**: The empty partition is then dropped from the `messages` table using `ALTER TABLE messages DROP PARTITION partitionName`.
7.  **Commit/Rollback**: The entire sequence of operations (creating archive table if needed, exchanging partition, recording metadata, dropping partition) is wrapped in a transaction. If any step fails, the transaction is rolled back to ensure data consistency.

This approach is significantly faster than copying data row by row, making it suitable for large-scale archival.

## Data Consistency and Integrity

* **Transactional Integrity**: By performing the archival steps within a single database transaction, the tool ensures that either the entire process succeeds or it fails cleanly, preventing partial archives or inconsistent states.
* **`EXCHANGE PARTITION` Atomicity**: The `EXCHANGE PARTITION` command itself is a metadata operation that is typically atomic at the storage engine level (for transactional engines like InnoDB). This minimizes the risk of data loss or corruption during the swap.
* **Metadata Tracking**: The `archived_message_partitions` table provides a consistent record of which data has been moved where. This metadata is crucial for querying historical data accurately.
* **Denormalized Fields**: The denormalized `last_message_*` fields in the `chats` table are not directly modified by the archival tool. It is assumed that application logic or database triggers are responsible for keeping these fields updated based on inserts/updates/deletes in the *primary* `messages` table. When a partition is archived, the data leaves the primary table, so these denormalized fields should correctly reflect the last message *remaining* in the primary table.

## Searching Archived Data

The provided tool handles the archival process. To search across both live and archived chat data, the application querying the data would need to implement the following logic:

1.  Identify the time range or other criteria for the search.
2.  Query the primary `messages` table, filtering by the search criteria.
3.  Query the `archived_message_partitions` table to find the names of archive tables that potentially contain data within the search time range or message ID range.
4.  For each identified archive table, query that table with the search criteria.
5.  Combine the results from the primary table query and all relevant archive table queries.

This requires application logic to use the metadata in `archived_message_partitions` to direct queries to the appropriate historical data sources.

## Setup and Usage

### Prerequisites

* MySQL Database Server (Ensure it supports Partitioning and `ALTER TABLE ... EXCHANGE PARTITION`. InnoDB storage engine is recommended).
* Java Development Kit (JDK).
* Maven (or a similar build tool like Gradle).

### Setup Steps

1.  **Clone the Repository**: Obtain the project code.
2.  **Configure Database Connection**: Edit the `src/main/resources/config.properties` file and update the `db.server`, `db.port`, `db.name`, `db.user`, and `db.password` properties to match your MySQL database setup.
3.  **Database Initialization**: The `DataSourceConfig.createMySQLDataSource` method is configured to automatically drop the database (if it exists), create it, and execute the `schema_and_dummy_data_script.sql` script when the application starts. This will set up the tables and insert dummy data.

### Archiving a Partition

1.  **Identify Partition**: Determine which partition you want to archive from the `messages` table. You can use the `DataSourceConfig.printTablePartitioning` method (e.g., call it from `Main` before archiving) to see the existing partitions and their names. For example, with the provided schema, partitions like `p202501`, `p202502`, etc., are available.
2.  **Configure Archiver**: The `Main.java` file demonstrates how to configure the `PartitionArchiver` using the `ArchiverConfig.Builder`. Ensure the `messagesTable`, `archivedMetadataTable`, and `archiveTableNameFunction` are correctly set. The provided `archiveTableNameFunction` assumes partition names start with 'p' followed by a date-like string (e.g., `pYYYYMM`).
3.  **Specify Partition to Archive**: In `Main.java`, locate the line `String partitionToArchive = "p202601";` and change `"p202601"` to the name of an existing partition you wish to archive (e.g., `"p202501"`). The provided schema does not have a `p202601` partition initially.
4.  **Run the Application**: Execute the `Main` class. If you are using Maven, you can often do this via your IDE or by running:
    ```bash
The tool will connect to the database, perform the archival steps for the specified partition, and print progress messages to the console.

### Example Archival Execution Flow (assuming archiving `p202501` as configured in Main)

1. The `Main` class starts.
2. `DataSourceConfig` loads `config.properties`, connects to MySQL, drops and recreates the database, and executes `schema_and_dummy_data_script.sql`. Tables are created and dummy data is inserted into `users`, `chats`, `chat_participants`, `messages`, and `user_chat_metadata`.
3. `Main` configures `ArchiverConfig` to use `messages`, `archived_message_partitions`, and the defined naming function.
4. `Main` creates a `PartitionArchiver` instance.
5. `Main` calls `DataSourceConfig.printTablePartitioning` to print the current partitions in the `messages` table.
6. selects the partition to archive (e.g., `p202501`).
7. `Main` calls `archiver.archivePartition("p202501")`.
8. The `PartitionArchiver` starts a transaction.
9. It confirms partition `p202501` exists in the `messages` table.
10. It calculates the archive table name: `messages_archive_202501` (based on the function).
11. It checks if `messages_archive_202501` exists. If not, it creates it and removes its partitioning.
12. It executes `ALTER TABLE messages EXCHANGE PARTITION p202501 WITH TABLE messages_archive_202501`. Data moves instantaneously.
13. It queries `messages_archive_202501` to find the min/max message IDs and timestamps.
14. It inserts a record into `archived_message_partitions` for `messages_archive_202501` with the retrieved metadata.
15. It drops the now-empty partition `p202501` from the `messages` table.
16. The transaction is committed.
17. The `messages` table is now smaller, and messages from the `p202501` partition are in the `messages_archive_202501` table, with details recorded in `archived_message_partitions`.

###  Improvements Needed

* **Automated Archival Scheduling**: Implement a mechanism (e.g., a background job or scheduled task) to automatically add new partition beforehand  (e.g., create next month or 3 month partition by alter query).
* **Automated Archival Scheduling**: Implement a mechanism (e.g., a background job or scheduled task) to automatically identify and archive partitions based on a defined policy (e.g., archive all partitions older than X months).
* **Comprehensive Search Layer**: Develop a dedicated data access layer or service that transparently queries both the live `messages` table and the relevant archive tables based on the metadata in `archived_message_partitions` when a historical search is performed.
* **Optimized Archived Table Indices**: Analyze query patterns on archived data and potentially add more specific indices to the archive tables if the default indices inherited from `messages` are insufficient.
* **Handling the `MAXVALUE` Partition**: Implement a process to manage the `MAXVALUE` partition. This typically involves splitting it periodically based on time range to create new concrete partitions that can eventually be archived.
* **Archival of Other Tables**: While the `messages` table is the primary concern due to volume, consider if other related tables (e.g., historical `chat_participants` entries) might also benefit from an archival strategy in the future.