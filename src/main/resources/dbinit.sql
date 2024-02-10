CREATE TABLE IF NOT EXISTS `language`
(
    `code`         VARCHAR(8)  NOT NULL, -- ISO 639-1 code
    `native_name`  VARCHAR(32) NOT NULL, -- The name of the language in the language itself
    `english_name` VARCHAR(32) NOT NULL, -- The name of the language in English
    PRIMARY KEY (`code`)
);

CREATE TABLE IF NOT EXISTS `user`
(
    `uuid`             VARCHAR(36) NOT NULL, -- Minecraft UUID
    `last_known_name`  VARCHAR(16) NOT NULL,
    `default_language` VARCHAR(8)  NOT NULL DEFAULT 'EN-US',
    PRIMARY KEY (`uuid`),
    FOREIGN KEY (`default_language`) REFERENCES `language` (`code`) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS `multilingual`
(
    `uuid`     VARCHAR(36) NOT NULL, -- Surrogate key
    `language` VARCHAR(8)  NOT NULL, -- ISO 639-1 code
    PRIMARY KEY (`uuid`, `language`),
    FOREIGN KEY (`uuid`) REFERENCES `user` (`uuid`) ON DELETE CASCADE,
    FOREIGN KEY (`language`) REFERENCES `language` (`code`) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS `message`
(
    `message_id` VARCHAR(36)  NOT NULL,
    `text`       VARCHAR(256) NOT NULL, -- We don't explicitly know the language of the message, so we store it here
    PRIMARY KEY (`message_id`),
    UNIQUE (`text`)
);

CREATE TABLE IF NOT EXISTS `message_translation`
(
    `uuid`       VARCHAR(36)  NOT NULL, -- Surrogate key
    `message_id` VARCHAR(36)  NOT NULL, -- To which message this translation belongs
    `language`   VARCHAR(8)   NOT NULL, -- ISO 639-1 code of translation
    `text`       VARCHAR(256) NOT NULL, -- The translated text
    PRIMARY KEY (`uuid`),
    FOREIGN KEY (`message_id`) REFERENCES `message` (`message_id`) ON DELETE CASCADE,
    FOREIGN KEY (`language`) REFERENCES `language` (`code`) ON DELETE CASCADE
);

CREATE UNIQUE INDEX `message_translation_index` ON `message_translation` (`message_id`, `language`);

CREATE TABLE IF NOT EXISTS `user_message`
(
    `uuid`       VARCHAR(36) NOT NULL, -- Surrogate key
    `user_id`    VARCHAR(36) NOT NULL, -- To which user this message belongs
    `message_id` VARCHAR(36) NOT NULL, -- To which message this translation belongs
    PRIMARY KEY (`uuid`),

    FOREIGN KEY (`uuid`) REFERENCES `user` (`uuid`) ON DELETE CASCADE,
    FOREIGN KEY (`message_id`) REFERENCES `message` (`message_id`) ON DELETE CASCADE
);

CREATE UNIQUE INDEX `user_message_index` ON `user_message` (`user_id`, `message_id`);
