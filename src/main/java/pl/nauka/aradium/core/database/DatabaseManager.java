package pl.nauka.aradium.core.database;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Centralny menedżer bazy danych (SQLite WAL / MySQL) udostępniający wspólne połączenie i schemat tabel dla całego Core.
 */
public class DatabaseManager {

    public enum DatabaseType {
        SQLITE,
        MYSQL
    }

    private static final List<String> SCHEMA_STATEMENTS = List.of(
            """
            CREATE TABLE IF NOT EXISTS economy_accounts (
                uuid VARCHAR(36) PRIMARY KEY,
                balance DOUBLE NOT NULL DEFAULT 0.0,
                updated_at BIGINT NOT NULL
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS skyblock_islands (
                island_id VARCHAR(36) PRIMARY KEY,
                owner_uuid VARCHAR(36) NOT NULL,
                world_name VARCHAR(64) NOT NULL,
                center_x INT NOT NULL,
                center_z INT NOT NULL,
                home_x DOUBLE NOT NULL,
                home_y DOUBLE NOT NULL,
                home_z DOUBLE NOT NULL,
                home_yaw FLOAT NOT NULL DEFAULT 0.0,
                home_pitch FLOAT NOT NULL DEFAULT 0.0,
                is_public BOOLEAN NOT NULL DEFAULT FALSE,
                border_color VARCHAR(16) NOT NULL DEFAULT 'BLUE',
                island_level DOUBLE NOT NULL DEFAULT 0.0,
                bank_balance DOUBLE NOT NULL DEFAULT 0.0,
                created_at BIGINT NOT NULL
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS skyblock_members (
                island_id VARCHAR(36) NOT NULL,
                player_uuid VARCHAR(36) NOT NULL,
                role VARCHAR(16) NOT NULL,
                joined_at BIGINT NOT NULL,
                PRIMARY KEY (island_id, player_uuid),
                FOREIGN KEY (island_id) REFERENCES skyblock_islands(island_id) ON DELETE CASCADE
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS skyblock_role_permissions (
                island_id VARCHAR(36) NOT NULL,
                role VARCHAR(16) NOT NULL,
                permission VARCHAR(32) NOT NULL,
                allowed BOOLEAN NOT NULL,
                PRIMARY KEY (island_id, role, permission),
                FOREIGN KEY (island_id) REFERENCES skyblock_islands(island_id) ON DELETE CASCADE
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS skyblock_upgrades (
                island_id VARCHAR(36) NOT NULL,
                upgrade_type VARCHAR(32) NOT NULL,
                tier INT NOT NULL DEFAULT 1,
                PRIMARY KEY (island_id, upgrade_type),
                FOREIGN KEY (island_id) REFERENCES skyblock_islands(island_id) ON DELETE CASCADE
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS skyblock_warps (
                island_id VARCHAR(36) NOT NULL,
                warp_name VARCHAR(32) NOT NULL,
                world_name VARCHAR(64) NOT NULL,
                x DOUBLE NOT NULL,
                y DOUBLE NOT NULL,
                z DOUBLE NOT NULL,
                yaw FLOAT NOT NULL DEFAULT 0.0,
                pitch FLOAT NOT NULL DEFAULT 0.0,
                icon VARCHAR(48) NOT NULL DEFAULT 'ENDER_PEARL',
                PRIMARY KEY (island_id, warp_name),
                FOREIGN KEY (island_id) REFERENCES skyblock_islands(island_id) ON DELETE CASCADE
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS skyblock_quests (
                island_id VARCHAR(36) NOT NULL,
                stage INT NOT NULL,
                quest_id VARCHAR(48) NOT NULL,
                progress_json TEXT NOT NULL,
                completed BOOLEAN NOT NULL DEFAULT FALSE,
                completed_at BIGINT NOT NULL DEFAULT 0,
                PRIMARY KEY (island_id, quest_id),
                FOREIGN KEY (island_id) REFERENCES skyblock_islands(island_id) ON DELETE CASCADE
            )
            """
    );

    private final DatabaseType type;
    private final String jdbcUrl;
    private final String username;
    private final String password;
    private final File sqliteFile;
    private final Logger logger;

    private Connection connection;

    public DatabaseManager(File sqliteFile, Logger logger) {
        this.type = DatabaseType.SQLITE;
        this.sqliteFile = Objects.requireNonNull(sqliteFile, "sqliteFile");
        this.jdbcUrl = "jdbc:sqlite:" + sqliteFile.getAbsolutePath();
        this.username = null;
        this.password = null;
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public DatabaseManager(String host, int port, String database, String username, String password, Logger logger) {
        this.type = DatabaseType.MYSQL;
        this.sqliteFile = null;
        this.jdbcUrl = "jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8";
        this.username = username;
        this.password = password;
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public synchronized void initialize() throws SQLException {
        if (type == DatabaseType.SQLITE && sqliteFile != null) {
            File parent = sqliteFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
        }

        Connection conn = getConnection();
        try (Statement stmt = conn.createStatement()) {
            if (type == DatabaseType.SQLITE) {
                stmt.execute("PRAGMA journal_mode = WAL;");
                stmt.execute("PRAGMA synchronous = NORMAL;");
                stmt.execute("PRAGMA foreign_keys = ON;");
            }
            for (String ddl : SCHEMA_STATEMENTS) {
                stmt.executeUpdate(ddl);
            }
        }
    }

    public synchronized Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            if (type == DatabaseType.SQLITE) {
                connection = DriverManager.getConnection(jdbcUrl);
            } else {
                connection = DriverManager.getConnection(jdbcUrl, username, password);
            }
        }
        return connection;
    }

    public synchronized boolean isConnected() {
        try {
            return connection != null && !connection.isClosed();
        } catch (SQLException ignored) {
            return false;
        }
    }

    public DatabaseType getType() {
        return type;
    }

    public synchronized void close() {
        if (connection == null) {
            return;
        }
        try {
            if (!connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Błąd podczas zamykania połączenia z bazą danych.", e);
        } finally {
            connection = null;
        }
    }
}
