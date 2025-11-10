package com.acme.platform.cli.service;

import com.acme.platform.cli.config.CliConfiguration;
import com.acme.platform.cli.model.PaginatedResult;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.*;

public class DatabaseService implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseService.class);
    private static DatabaseService instance;
    private final HikariDataSource dataSource;
    private final CliConfiguration config;

    private DatabaseService() {
        this.config = CliConfiguration.getInstance();
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(config.getDbUrl());
        hikariConfig.setUsername(config.getDbUser());
        hikariConfig.setPassword(config.getDbPassword());
        hikariConfig.setMaximumPoolSize(config.getDbMaxPoolSize());
        hikariConfig.setConnectionTimeout(10000);
        hikariConfig.setIdleTimeout(600000);
        hikariConfig.setMaxLifetime(1800000);

        this.dataSource = new HikariDataSource(hikariConfig);
        logger.info("Database connection pool initialized");
    }

    public static synchronized DatabaseService getInstance() {
        if (instance == null) {
            instance = new DatabaseService();
        }
        return instance;
    }

    public PaginatedResult queryTable(String tableName, int page, Integer pageSize) throws SQLException {
        if (pageSize == null) {
            pageSize = config.getCliPageSize();
        }

        // Validate table name to prevent SQL injection
        if (!isValidTableName(tableName)) {
            throw new IllegalArgumentException("Invalid table name: " + tableName);
        }

        int offset = (page - 1) * pageSize;

        // Get total count
        long totalRecords;
        String countSql = String.format("SELECT COUNT(*) FROM %s", tableName);
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(countSql)) {
            rs.next();
            totalRecords = rs.getLong(1);
        }

        // Get paginated data
        List<Map<String, Object>> data = new ArrayList<>();
        String querySql = String.format("SELECT * FROM %s LIMIT %d OFFSET %d", tableName, pageSize, offset);

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(querySql)) {

            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();

            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= columnCount; i++) {
                    String columnName = metaData.getColumnName(i);
                    Object value = rs.getObject(i);
                    row.put(columnName, value);
                }
                data.add(row);
            }
        }

        PaginatedResult.Pagination pagination = new PaginatedResult.Pagination(page, pageSize, totalRecords);
        return new PaginatedResult(data, pagination);
    }

    public List<String> listTables() throws SQLException {
        List<String> tables = new ArrayList<>();
        String sql = "SELECT tablename FROM pg_tables WHERE schemaname = 'public' ORDER BY tablename";

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                tables.add(rs.getString("tablename"));
            }
        }

        return tables;
    }

    public Map<String, Object> getTableInfo(String tableName) throws SQLException {
        if (!isValidTableName(tableName)) {
            throw new IllegalArgumentException("Invalid table name: " + tableName);
        }

        Map<String, Object> info = new LinkedHashMap<>();

        // Get row count
        String countSql = String.format("SELECT COUNT(*) as count FROM %s", tableName);
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(countSql)) {
            if (rs.next()) {
                info.put("rowCount", rs.getLong("count"));
            }
        }

        // Get columns
        List<Map<String, String>> columns = new ArrayList<>();
        String columnSql = String.format(
                "SELECT column_name, data_type, is_nullable " +
                        "FROM information_schema.columns " +
                        "WHERE table_schema = 'public' AND table_name = '%s' " +
                        "ORDER BY ordinal_position", tableName);

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(columnSql)) {

            while (rs.next()) {
                Map<String, String> column = new LinkedHashMap<>();
                column.put("name", rs.getString("column_name"));
                column.put("type", rs.getString("data_type"));
                column.put("nullable", rs.getString("is_nullable"));
                columns.add(column);
            }
        }
        info.put("columns", columns);

        return info;
    }

    private boolean isValidTableName(String tableName) {
        // Allow only alphanumeric characters and underscores
        return tableName != null && tableName.matches("^[a-zA-Z0-9_]+$");
    }

    public void testConnection() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            logger.info("Database connection test successful");
        }
    }

    @Override
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            logger.info("Database connection pool closed");
        }
    }
}
