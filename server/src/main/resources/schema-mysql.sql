DROP
    SCHEMA IF EXISTS core;

CREATE
    SCHEMA core;

USE core;

CREATE
    TABLE
        USER(
            id BIGINT AUTO_INCREMENT,
            display_name VARCHAR(191) NOT NULL UNIQUE,
            class_id INT NULL,
            email VARCHAR(128) NULL,
            is_not_banned BOOLEAN DEFAULT FALSE,
            account_created DATETIME NOT NULL,
            user_key BINARY(16) NOT NULL,
            PRIMARY KEY(id),
            INDEX class_id(
                class_id ASC
            ),
            UNIQUE INDEX display_name_UNIQUE(
                display_name ASC
            )
        ) ENGINE = InnoDB DEFAULT CHARACTER
    SET
        = utf8mb4;

CREATE
    TABLE
        class(
            id BIGINT AUTO_INCREMENT,
            name VARCHAR(191) NOT NULL UNIQUE,
            PRIMARY KEY(id)
        ) ENGINE = InnoDB DEFAULT CHARACTER
    SET
        = utf8mb4;

CREATE
    TABLE
        module(
            id BIGINT AUTO_INCREMENT,
            name VARCHAR(191) NOT NULL UNIQUE,
            is_flag_static BOOLEAN DEFAULT FALSE,
            static_flag VARCHAR(64) NULL,
            module_key BINARY(64) NOT NULL,
            is_open BOOLEAN DEFAULT FALSE,
            PRIMARY KEY(id)
        ) ENGINE = InnoDB DEFAULT CHARACTER
    SET
        = utf8mb4;

CREATE
    TABLE
        csrf_attack(
            id BIGINT AUTO_INCREMENT,
            pseudonym VARCHAR(128) NOT NULL,
            module_name VARCHAR(191) NOT NULL,
            started TIMESTAMP NOT NULL,
            finished TIMESTAMP,
            PRIMARY KEY(id),
            FOREIGN KEY(module_name) REFERENCES module(name)
        ) ENGINE = InnoDB DEFAULT CHARACTER
    SET
        = utf8mb4;

CREATE
    TABLE
        correction(
            id BIGINT AUTO_INCREMENT,
            user_id BIGINT NOT NULL,
            amount INT NOT NULL,
            TIME TIMESTAMP NOT NULL,
            description VARCHAR(191),
            PRIMARY KEY(id),
            FOREIGN KEY(user_id) REFERENCES USER(id)
        ) ENGINE = InnoDB DEFAULT CHARACTER
    SET
        = utf8mb4;

CREATE
    TABLE
        module_point(
            id BIGINT AUTO_INCREMENT,
            module_name VARCHAR(191) NOT NULL,
            submission_rank INT NOT NULL,
            points INT NOT NULL,
            PRIMARY KEY(id),
            UNIQUE KEY(
                module_name,
                submission_rank
            ),
            FOREIGN KEY(
                module_name
            ) REFERENCES module(name)
        ) ENGINE = InnoDB DEFAULT CHARACTER
    SET
        = utf8mb4;

CREATE
    TABLE
        user_auth(
            id BIGINT AUTO_INCREMENT,
            user_id BIGINT UNIQUE NOT NULL,
            is_enabled BOOLEAN DEFAULT FALSE,
            bad_login_count INT DEFAULT 0,
            is_admin BOOLEAN DEFAULT FALSE NOT NULL,
            suspension_message VARCHAR(191),
            suspended_until DATETIME NULL DEFAULT NULL,
            last_login DATETIME NULL DEFAULT NULL,
            last_login_method VARCHAR(10) DEFAULT NULL,
            PRIMARY KEY(id),
            FOREIGN KEY(user_id) REFERENCES USER(id)
        ) ENGINE = InnoDB DEFAULT CHARACTER
    SET
        = utf8mb4;

CREATE
    TABLE
        saml_auth(
            id BIGINT AUTO_INCREMENT,
            user_id BIGINT NOT NULL UNIQUE,
            saml_id VARCHAR(40) NOT NULL UNIQUE,
            PRIMARY KEY(id),
            FOREIGN KEY(user_id) REFERENCES USER(id)
        ) ENGINE = InnoDB DEFAULT CHARACTER
    SET
        = utf8mb4;

CREATE
    TABLE
        password_auth(
            id BIGINT AUTO_INCREMENT,
            user_id BIGINT NOT NULL UNIQUE,
            login_name VARCHAR(191) NOT NULL UNIQUE,
            hashed_password VARCHAR(191) NOT NULL,
            is_password_non_expired BOOLEAN DEFAULT FALSE,
            PRIMARY KEY(id),
            FOREIGN KEY(user_id) REFERENCES USER(id)
        ) ENGINE = InnoDB DEFAULT CHARACTER
    SET
        = utf8mb4;

CREATE
    TABLE
        submission(
            id BIGINT AUTO_INCREMENT,
            user_id BIGINT NOT NULL,
            module_name VARCHAR(191) NOT NULL,
            TIME DATETIME NULL DEFAULT NULL,
            is_valid BOOLEAN NOT NULL,
            flag VARCHAR(191) NOT NULL,
            valid_or_null BOOLEAN AS(
                IF(
                    is_valid = TRUE,
                    TRUE,
                    NULL
                )
            ) stored,
            PRIMARY KEY(id),
            UNIQUE KEY(
                user_id,
                module_name,
                valid_or_null
            ),
            FOREIGN KEY(
                `user_id`
            ) REFERENCES USER(id),
            FOREIGN KEY(
                `module_name`
            ) REFERENCES module(name)
        ) ENGINE = InnoDB DEFAULT CHARACTER
    SET
        = utf8mb4;

CREATE
    TABLE
        configuration(
            id BIGINT AUTO_INCREMENT,
            config_key VARCHAR(191) NOT NULL UNIQUE,
            value VARCHAR(191) NOT NULL,
            PRIMARY KEY(id)
        ) ENGINE = InnoDB DEFAULT CHARACTER
    SET
        = utf8mb4;

CREATE
    VIEW ranked_submission AS WITH ranks AS(
        SELECT
            RANK() OVER(
                PARTITION BY module_name
            ORDER BY
                TIME
            ) AS 'rank',
            id AS submission_id,
            user_id,
            module_name,
            TIME,
            flag
        FROM
            submission
        WHERE
            is_valid = TRUE
    ) SELECT
        submission_id,
        user_id,
        `RANK`,
        ranks.module_name,
        TIME,
        flag,
        base_score.points AS base_score,
        COALESCE(
            bonus_score.points,
            0
        ) AS bonus_score,
        base_score.points + COALESCE(
            bonus_score.points,
            0
        ) AS score
    FROM
        ranks
    LEFT JOIN module_point AS bonus_score ON
        (
            ranks.module_name = bonus_score.module_name
            AND `RANK`= bonus_score.submission_rank
        )
    INNER JOIN module_point AS base_score ON
        (
            ranks.module_name = base_score.module_name
            AND base_score.submission_rank = 0
        );

CREATE
    VIEW scoreboard AS SELECT
        RANK() OVER(
        ORDER BY
            SUM( score ) DESC
        ) AS 'rank',
        user_id,
        display_name,
        CAST(
            SUM( score ) AS SIGNED
        ) AS score,
        SUM( CASE WHEN `RANK`= 1 THEN 1 ELSE 0 END ) AS gold_medals,
        SUM( CASE WHEN `RANK`= 2 THEN 1 ELSE 0 END ) AS silver_medals,
        SUM( CASE WHEN `RANK`= 3 THEN 1 ELSE 0 END ) AS bronze_medals
    FROM
        (
            SELECT
                user_id,
                score,
                `RANK`,
                0 AS display_name
            FROM
                ranked_submission
        UNION ALL SELECT
                user_id,
                amount AS score,
                0,
                0
            FROM
                correction
        UNION ALL SELECT
                id AS user_id,
                0,
                0,
                display_name AS display_name
            FROM
                USER
        ) AS all_scores
    GROUP BY
        user_id
    ORDER BY
        'rank' DESC;