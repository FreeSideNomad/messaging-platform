package com.acme.platform.cli.commands;

import com.acme.platform.cli.model.PaginatedResult;
import com.acme.platform.cli.service.DatabaseService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.List;
import java.util.Map;

@Command(
        name = "db",
        description = "Database query operations",
        subcommands = {
                DatabaseCommands.Query.class,
                DatabaseCommands.ListTables.class,
                DatabaseCommands.TableInfo.class
        }
)
public class DatabaseCommands {

    @Command(name = "query", description = "Query a database table with pagination")
    static class Query implements Runnable {
        @Parameters(index = "0", description = "Table name to query")
        private String table;

        @Option(names = {"-p", "--page"}, description = "Page number (default: 1)", defaultValue = "1")
        private int page;

        @Option(names = {"-s", "--page-size"}, description = "Number of records per page (default: from config)")
        private Integer pageSize;

        @Option(names = {"-f", "--format"}, description = "Output format: table or json (default: table)", defaultValue = "table")
        private String format;

        @Override
        public void run() {
            try (DatabaseService dbService = DatabaseService.getInstance()) {
                PaginatedResult result = dbService.queryTable(table, page, pageSize);

                if ("json".equalsIgnoreCase(format)) {
                    printJson(result);
                } else {
                    printTable(result);
                }
            } catch (Exception e) {
                System.err.println("Error querying table: " + e.getMessage());
                System.exit(1);
            }
        }

        private void printJson(PaginatedResult result) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                mapper.enable(SerializationFeature.INDENT_OUTPUT);
                String json = mapper.writeValueAsString(result);
                System.out.println(json);
            } catch (Exception e) {
                System.err.println("Error formatting JSON: " + e.getMessage());
            }
        }

        private void printTable(PaginatedResult result) {
            if (result.getData().isEmpty()) {
                System.out.println("No data found.");
                return;
            }

            // Get column names from first row
            List<String> columns = List.copyOf(result.getData().get(0).keySet());

            // Calculate column widths
            Map<String, Integer> columnWidths = new java.util.HashMap<>();
            for (String column : columns) {
                int maxWidth = column.length();
                for (Map<String, Object> row : result.getData()) {
                    Object value = row.get(column);
                    int valueWidth = value != null ? value.toString().length() : 4; // "null" length
                    maxWidth = Math.max(maxWidth, valueWidth);
                }
                columnWidths.put(column, Math.min(maxWidth, 50)); // Cap at 50 chars
            }

            // Print header
            printSeparator(columns, columnWidths);
            printRow(columns, columnWidths, columns.stream()
                    .collect(java.util.stream.Collectors.toMap(c -> c, c -> c)));
            printSeparator(columns, columnWidths);

            // Print data rows
            for (Map<String, Object> row : result.getData()) {
                printRow(columns, columnWidths, row);
            }
            printSeparator(columns, columnWidths);

            // Print pagination info
            PaginatedResult.Pagination pagination = result.getPagination();
            System.out.printf("\nPage %d of %d (Total records: %d)\n",
                    pagination.getPage(),
                    pagination.getTotalPages(),
                    pagination.getTotalRecords());
        }

        private void printSeparator(List<String> columns, Map<String, Integer> widths) {
            System.out.print("+");
            for (String column : columns) {
                System.out.print("-".repeat(widths.get(column) + 2) + "+");
            }
            System.out.println();
        }

        private void printRow(List<String> columns, Map<String, Integer> widths, Map<String, Object> row) {
            System.out.print("|");
            for (String column : columns) {
                Object value = row.get(column);
                String valueStr = value != null ? value.toString() : "null";
                if (valueStr.length() > widths.get(column)) {
                    valueStr = valueStr.substring(0, widths.get(column) - 3) + "...";
                }
                System.out.printf(" %-" + widths.get(column) + "s |", valueStr);
            }
            System.out.println();
        }
    }

    @Command(name = "list", description = "List all available tables")
    static class ListTables implements Runnable {
        @Option(names = {"-f", "--format"}, description = "Output format: table or json (default: table)", defaultValue = "table")
        private String format;

        @Override
        public void run() {
            try (DatabaseService dbService = DatabaseService.getInstance()) {
                List<String> tables = dbService.listTables();

                if ("json".equalsIgnoreCase(format)) {
                    ObjectMapper mapper = new ObjectMapper();
                    mapper.enable(SerializationFeature.INDENT_OUTPUT);
                    System.out.println(mapper.writeValueAsString(Map.of("tables", tables)));
                } else {
                    System.out.println("Available Tables:");
                    System.out.println("=================");
                    for (String table : tables) {
                        System.out.println("  - " + table);
                    }
                    System.out.println("\nTotal: " + tables.size() + " tables");
                }
            } catch (Exception e) {
                System.err.println("Error listing tables: " + e.getMessage());
                System.exit(1);
            }
        }
    }

    @Command(name = "info", description = "Get information about a specific table")
    static class TableInfo implements Runnable {
        @Parameters(index = "0", description = "Table name")
        private String table;

        @Option(names = {"-f", "--format"}, description = "Output format: table or json (default: table)", defaultValue = "table")
        private String format;

        @Override
        public void run() {
            try (DatabaseService dbService = DatabaseService.getInstance()) {
                Map<String, Object> info = dbService.getTableInfo(table);

                if ("json".equalsIgnoreCase(format)) {
                    ObjectMapper mapper = new ObjectMapper();
                    mapper.enable(SerializationFeature.INDENT_OUTPUT);
                    System.out.println(mapper.writeValueAsString(info));
                } else {
                    System.out.println("Table: " + table);
                    System.out.println("=".repeat(50));
                    System.out.println("Row Count: " + info.get("rowCount"));
                    System.out.println("\nColumns:");
                    @SuppressWarnings("unchecked")
                    List<Map<String, String>> columns = (List<Map<String, String>>) info.get("columns");
                    for (Map<String, String> column : columns) {
                        System.out.printf("  - %s (%s) %s\n",
                                column.get("name"),
                                column.get("type"),
                                "YES".equals(column.get("nullable")) ? "NULL" : "NOT NULL");
                    }
                }
            } catch (Exception e) {
                System.err.println("Error getting table info: " + e.getMessage());
                System.exit(1);
            }
        }
    }
}
