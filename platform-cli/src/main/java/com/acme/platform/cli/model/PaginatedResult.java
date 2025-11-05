package com.acme.platform.cli.model;

import java.util.List;
import java.util.Map;

public class PaginatedResult {
    private List<Map<String, Object>> data;
    private Pagination pagination;

    public PaginatedResult(List<Map<String, Object>> data, Pagination pagination) {
        this.data = data;
        this.pagination = pagination;
    }

    public List<Map<String, Object>> getData() {
        return data;
    }

    public void setData(List<Map<String, Object>> data) {
        this.data = data;
    }

    public Pagination getPagination() {
        return pagination;
    }

    public void setPagination(Pagination pagination) {
        this.pagination = pagination;
    }

    public static class Pagination {
        private int page;
        private int pageSize;
        private long totalRecords;
        private int totalPages;

        public Pagination(int page, int pageSize, long totalRecords) {
            this.page = page;
            this.pageSize = pageSize;
            this.totalRecords = totalRecords;
            this.totalPages = (int) Math.ceil((double) totalRecords / pageSize);
        }

        public int getPage() {
            return page;
        }

        public void setPage(int page) {
            this.page = page;
        }

        public int getPageSize() {
            return pageSize;
        }

        public void setPageSize(int pageSize) {
            this.pageSize = pageSize;
        }

        public long getTotalRecords() {
            return totalRecords;
        }

        public void setTotalRecords(long totalRecords) {
            this.totalRecords = totalRecords;
        }

        public int getTotalPages() {
            return totalPages;
        }

        public void setTotalPages(int totalPages) {
            this.totalPages = totalPages;
        }
    }
}
