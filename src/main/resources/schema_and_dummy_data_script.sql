START TRANSACTION;

CREATE TABLE users (
    user_id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    email VARCHAR(255) NULL UNIQUE,
    full_name VARCHAR(255) NULL,
    avatar_url VARCHAR(1024) NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_active BOOLEAN DEFAULT TRUE
) ENGINE=InnoDB ;

CREATE TABLE chats (
    chat_id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    chat_type ENUM('direct', 'group') NOT NULL,
    group_name VARCHAR(255),
    group_avatar_url VARCHAR(1024),
    created_by_user_id BIGINT UNSIGNED,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    last_message_id BIGINT UNSIGNED,
    last_message_sender_id BIGINT UNSIGNED,
    last_message_content_preview VARCHAR(500),
    last_message_created_at TIMESTAMP(6),

    CONSTRAINT fk_chats_created_by_user FOREIGN KEY (created_by_user_id) REFERENCES users(user_id) ON DELETE SET NULL,
    CONSTRAINT fk_chats_last_message_sender FOREIGN KEY (last_message_sender_id) REFERENCES users(user_id) ON DELETE SET NULL
) ENGINE=InnoDB ;

CREATE TABLE chat_participants (
    chat_participant_id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    chat_id BIGINT UNSIGNED NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    role ENUM('member', 'admin', 'owner') DEFAULT 'member',
    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    added_by_user_id BIGINT UNSIGNED,

    UNIQUE KEY uk_chat_user (chat_id, user_id),
    CONSTRAINT fk_participants_chat FOREIGN KEY (chat_id) REFERENCES chats(chat_id) ON DELETE CASCADE,
    CONSTRAINT fk_participants_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    CONSTRAINT fk_participants_added_by FOREIGN KEY (added_by_user_id) REFERENCES users(user_id) ON DELETE SET NULL
) ENGINE=InnoDB ;

CREATE TABLE messages (
    message_id BIGINT UNSIGNED AUTO_INCREMENT,
    chat_id BIGINT UNSIGNED NOT NULL,
    sender_id BIGINT UNSIGNED NOT NULL,
    message_type ENUM('text', 'image', 'video', 'file', 'audio', 'system', 'sticker', 'location') DEFAULT 'text',
    content TEXT,
    media_url VARCHAR(1024),
    thumbnail_url VARCHAR(1024),
    file_metadata JSON,
    parent_message_id BIGINT UNSIGNED,
    parent_message_preview VARCHAR(100),
    created_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at DATETIME NULL,
    PRIMARY KEY (message_id, created_at),
    INDEX idx_chat_created_at (chat_id, created_at DESC),
    INDEX idx_created_at (created_at),
    INDEX idx_sender_id (sender_id),
    INDEX idx_message_type (message_type)
)
ENGINE = InnoDB
PARTITION BY RANGE (TO_DAYS(created_at)) (
    PARTITION p202501 VALUES LESS THAN (TO_DAYS('2025-02-01')),
    PARTITION p202502 VALUES LESS THAN (TO_DAYS('2025-03-01')),
    PARTITION p202503 VALUES LESS THAN (TO_DAYS('2025-04-01')),
    PARTITION p202504 VALUES LESS THAN (TO_DAYS('2025-05-01')),
    PARTITION p202505 VALUES LESS THAN (TO_DAYS('2025-06-01')),
    PARTITION pmax    VALUES LESS THAN MAXVALUE
);



CREATE TABLE archived_message_partitions (
    archive_table_name VARCHAR(255) NOT NULL PRIMARY KEY,
    start_timestamp DATETIME(3) NOT NULL,
    end_timestamp DATETIME(3) NOT NULL,
    min_message_id BIGINT UNSIGNED NOT NULL, -- Minimum message_id in this archive table
    max_message_id BIGINT UNSIGNED NOT NULL, -- Maximum message_id in this archive table
    archived_at DATETIME(3) NOT NULL, -- When the partition was archived
    INDEX idx_date_range (start_timestamp, end_timestamp),
    INDEX idx_message_id_range (min_message_id, max_message_id) -- Optional index for searching by ID range
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


CREATE TABLE user_chat_metadata (
    user_chat_metadata_id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT UNSIGNED NOT NULL,
    chat_id BIGINT UNSIGNED NOT NULL,
    last_read_at TIMESTAMP(6),
    unread_count INT UNSIGNED DEFAULT 0,
    is_muted BOOLEAN DEFAULT FALSE,
    is_pinned BOOLEAN DEFAULT FALSE,
    is_archived_by_user BOOLEAN DEFAULT FALSE,
    hide_chat_until_new_message BOOLEAN DEFAULT FALSE,
    custom_notification_settings JSON,
    last_cleared_at TIMESTAMP(6),

    UNIQUE KEY uk_user_chat (user_id, chat_id),
    CONSTRAINT fk_usermeta_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    CONSTRAINT fk_usermeta_chat FOREIGN KEY (chat_id) REFERENCES chats(chat_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO users (username, password_hash, email, full_name, avatar_url, is_active)
VALUES
    ('user001', 'hashedpassword001', 'user001@example.com', 'Full Name 1', 'https://example.com/avatar1.jpg', TRUE),
    ('user002', 'hashedpassword002', 'user002@example.com', 'Full Name 2', 'https://example.com/avatar2.jpg', TRUE),
    ('user003', 'hashedpassword003', 'user003@example.com', 'Full Name 3', 'https://example.com/avatar3.jpg', TRUE),
    ('user004', 'hashedpassword004', 'user004@example.com', 'Full Name 4', 'https://example.com/avatar4.jpg', TRUE),
    ('user005', 'hashedpassword005', 'user005@example.com', 'Full Name 5', 'https://example.com/avatar5.jpg', TRUE),
    ('user006', 'hashedpassword006', 'user006@example.com', 'Full Name 6', 'https://example.com/avatar6.jpg', TRUE),
    ('user007', 'hashedpassword007', 'user007@example.com', 'Full Name 7', 'https://example.com/avatar7.jpg', TRUE),
    ('user008', 'hashedpassword008', 'user008@example.com', 'Full Name 8', 'https://example.com/avatar8.jpg', TRUE),
    ('user009', 'hashedpassword009', 'user009@example.com', 'Full Name 9', 'https://example.com/avatar9.jpg', TRUE),
    ('user010', 'hashedpassword010', 'user010@example.com', 'Full Name 10', 'https://example.com/avatar10.jpg', TRUE);

INSERT INTO chats (chat_type, group_name, group_avatar_url, created_by_user_id)
VALUES
    ('direct', NULL, NULL, 1),
    ('direct', NULL, NULL, 2),
    ('direct', NULL, NULL, 3),
    ('group', 'Group Chat 1', 'https://example.com/group-avatar1.jpg', 4),
    ('group', 'Group Chat 2', 'https://example.com/group-avatar2.jpg', 5),
    ('group', 'Group Chat 3', 'https://example.com/group-avatar3.jpg', 6),
    ('group', 'Group Chat 4', 'https://example.com/group-avatar4.jpg', 7),
    ('group', 'Group Chat 5', 'https://example.com/group-avatar5.jpg', 8),
    ('direct', NULL, NULL, 9),
    ('direct', NULL, NULL, 10);

INSERT INTO chat_participants (chat_id, user_id, role)
VALUES
    (1, 1, 'member'),
    (2, 2, 'member'),
    (3, 3, 'member'),
    (4, 4, 'owner'),
    (4, 5, 'admin'),
    (5, 6, 'member'),
    (5, 7, 'member'),
    (6, 8, 'owner'),
    (7, 9, 'admin'),
    (8, 10, 'member');

START TRANSACTION;


INSERT INTO messages (chat_id, sender_id, message_type, content, created_at)
VALUES
    (1, 1, 'text', 'Message 1 in January', '2025-01-05 10:00:00'),
    (1, 2, 'text', 'Message 2 in January', '2025-01-06 11:00:00'),
    (1, 3, 'text', 'Message 3 in January', '2025-01-07 12:00:00'),
    (1, 4, 'text', 'Message 4 in January', '2025-01-08 13:00:00'),
    (1, 5, 'text', 'Message 5 in January', '2025-01-09 14:00:00'),
    (1, 6, 'text', 'Message 6 in January', '2025-01-10 15:00:00'),
    (2, 7, 'text', 'Message 7 in January', '2025-01-11 16:00:00'),
    (2, 8, 'text', 'Message 8 in January', '2025-01-12 17:00:00'),
    (2, 9, 'text', 'Message 9 in January', '2025-01-13 18:00:00'),
    (2, 10, 'text', 'Message 10 in January', '2025-01-14 19:00:00'),
    (3, 1, 'text', 'Message 11 in January', '2025-01-15 20:00:00'),
    (3, 2, 'text', 'Message 12 in January', '2025-01-16 21:00:00'),
    (3, 3, 'text', 'Message 13 in January', '2025-01-17 22:00:00'),
    (3, 4, 'text', 'Message 14 in January', '2025-01-18 23:00:00'),
    (3, 5, 'text', 'Message 15 in January', '2025-01-19 00:00:00');

INSERT INTO messages (chat_id, sender_id, message_type, content, created_at)
VALUES
    (4, 6, 'text', 'Message 1 in February', '2025-02-01 10:00:00'),
    (4, 7, 'text', 'Message 2 in February', '2025-02-02 11:00:00'),
    (4, 8, 'text', 'Message 3 in February', '2025-02-03 12:00:00'),
    (4, 9, 'text', 'Message 4 in February', '2025-02-04 13:00:00'),
    (4, 10, 'text', 'Message 5 in February', '2025-02-05 14:00:00'),
    (4, 1, 'text', 'Message 6 in February', '2025-02-06 15:00:00'),
    (5, 2, 'text', 'Message 7 in February', '2025-02-07 16:00:00'),
    (5, 3, 'text', 'Message 8 in February', '2025-02-08 17:00:00'),
    (5, 4, 'text', 'Message 9 in February', '2025-02-09 18:00:00'),
    (5, 5, 'text', 'Message 10 in February', '2025-02-10 19:00:00'),
    (6, 6, 'text', 'Message 11 in February', '2025-02-11 20:00:00'),
    (6, 7, 'text', 'Message 12 in February', '2025-02-12 21:00:00'),
    (6, 8, 'text', 'Message 13 in February', '2025-02-13 22:00:00'),
    (6, 9, 'text', 'Message 14 in February', '2025-02-14 23:00:00'),
    (6, 10, 'text', 'Message 15 in February', '2025-02-15 00:00:00'),
    (6, 1, 'text', 'Message 16 in February', '2025-02-16 01:00:00'),
    (6, 2, 'text', 'Message 17 in February', '2025-02-17 02:00:00'),
    (6, 3, 'text', 'Message 18 in February', '2025-02-18 03:00:00'),
    (6, 4, 'text', 'Message 19 in February', '2025-02-19 04:00:00');

INSERT INTO messages (chat_id, sender_id, message_type, content, created_at)
VALUES
    (7, 5, 'text', 'Message 1 in March', '2025-03-01 10:00:00'),
    (7, 6, 'text', 'Message 2 in March', '2025-03-02 11:00:00'),
    (7, 7, 'text', 'Message 3 in March', '2025-03-03 12:00:00'),
    (7, 8, 'text', 'Message 4 in March', '2025-03-04 13:00:00'),
    (7, 9, 'text', 'Message 5 in March', '2025-03-05 14:00:00'),
    (7, 10, 'text', 'Message 6 in March', '2025-03-06 15:00:00'),
    (8, 1, 'text', 'Message 7 in March', '2025-03-07 16:00:00'),
    (8, 2, 'text', 'Message 8 in March', '2025-03-08 17:00:00'),
    (8, 3, 'text', 'Message 9 in March', '2025-03-09 18:00:00'),
    (8, 4, 'text', 'Message 10 in March', '2025-03-10 19:00:00'),
    (9, 5, 'text', 'Message 11 in March', '2025-03-11 20:00:00'),
    (9, 6, 'text', 'Message 12 in March', '2025-03-12 21:00:00'),
    (9, 7, 'text', 'Message 13 in March', '2025-03-13 22:00:00'),
    (9, 8, 'text', 'Message 14 in March', '2025-03-14 23:00:00'),
    (9, 9, 'text', 'Message 15 in March', '2025-03-15 00:00:00'),
    (9, 10, 'text', 'Message 16 in March', '2025-03-16 01:00:00'),
    (9, 1, 'text', 'Message 17 in March', '2025-03-17 02:00:00');

INSERT INTO messages (chat_id, sender_id, message_type, content, created_at)
VALUES
    (10, 2, 'text', 'Message 1 in April', '2025-04-01 10:00:00'),
    (10, 3, 'text', 'Message 2 in April', '2025-04-02 11:00:00'),
    (10, 4, 'text', 'Message 3 in April', '2025-04-03 12:00:00'),
    (10, 5, 'text', 'Message 4 in April', '2025-04-04 13:00:00'),
    (10, 6, 'text', 'Message 5 in April', '2025-04-05 14:00:00'),
    (10, 7, 'text', 'Message 6 in April', '2025-04-06 15:00:00'),
    (11, 8, 'text', 'Message 7 in April', '2025-04-07 16:00:00'),
    (11, 9, 'text', 'Message 8 in April', '2025-04-08 17:00:00'),
    (11, 10, 'text', 'Message 9 in April', '2025-04-09 18:00:00'),
    (11, 1, 'text', 'Message 10 in April', '2025-04-10 19:00:00'),
    (11, 2, 'text', 'Message 11 in April', '2025-04-11 20:00:00'),
    (11, 3, 'text', 'Message 12 in April', '2025-04-12 21:00:00'),
    (11, 4, 'text', 'Message 13 in April', '2025-04-13 22:00:00'),
    (11, 5, 'text', 'Message 14 in April', '2025-04-14 23:00:00'),
    (11, 6, 'text', 'Message 15 in April', '2025-04-15 00:00:00');

INSERT INTO messages (chat_id, sender_id, message_type, content, created_at)
VALUES
    (12, 7, 'text', 'Message 1 in May', '2025-05-01 10:00:00'),
    (12, 8, 'text', 'Message 2 in May', '2025-05-02 11:00:00'),
    (12, 9, 'text', 'Message 3 in May', '2025-05-03 12:00:00'),
    (12, 10, 'text', 'Message 4 in May', '2025-05-04 13:00:00'),
    (12, 1, 'text', 'Message 5 in May', '2025-05-05 14:00:00'),
    (12, 2, 'text', 'Message 6 in May', '2025-05-06 15:00:07');

INSERT INTO user_chat_metadata (user_id, chat_id, last_read_at, unread_count, is_muted)
VALUES
    (1, 1, NOW(), 0, FALSE),
    (2, 1, NOW(), 1, TRUE),
    (3, 2, NOW(), 2, FALSE),
    (4, 3, NOW(), 0, FALSE),
    (5, 4, NOW(), 3, TRUE),
    (6, 5, NOW(), 1, FALSE),
    (7, 6, NOW(), 0, TRUE),
    (8, 7, NOW(), 0, FALSE),
    (9, 8, NOW(), 0, FALSE),
    (10, 9, NOW(), 2, TRUE);

COMMIT;
