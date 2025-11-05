package com.acme.platform.cli.model;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class PaginatedResultTest {

    @Test
    void testPaginatedResult_construction() {
        List<Map<String, Object>> data = new ArrayList<>();
        Map<String, Object> row = new HashMap<>();
        row.put("id", 1);
        row.put("name", "test");
        data.add(row);

        PaginatedResult.Pagination pagination = new PaginatedResult.Pagination(1, 20, 100);
        PaginatedResult result = new PaginatedResult(data, pagination);

        assertThat(result.getData()).hasSize(1);
        assertThat(result.getPagination()).isNotNull();
    }

    @Test
    void testPaginatedResult_settersAndGetters() {
        List<Map<String, Object>> data = new ArrayList<>();
        PaginatedResult.Pagination pagination = new PaginatedResult.Pagination(1, 20, 100);
        PaginatedResult result = new PaginatedResult(data, pagination);

        List<Map<String, Object>> newData = new ArrayList<>();
        result.setData(newData);
        assertThat(result.getData()).isSameAs(newData);

        PaginatedResult.Pagination newPagination = new PaginatedResult.Pagination(2, 20, 100);
        result.setPagination(newPagination);
        assertThat(result.getPagination()).isSameAs(newPagination);
    }

    @Test
    void testPagination_construction() {
        PaginatedResult.Pagination pagination = new PaginatedResult.Pagination(1, 20, 100);

        assertThat(pagination.getPage()).isEqualTo(1);
        assertThat(pagination.getPageSize()).isEqualTo(20);
        assertThat(pagination.getTotalRecords()).isEqualTo(100);
        assertThat(pagination.getTotalPages()).isEqualTo(5);
    }

    @Test
    void testPagination_calculatesPages_exactDivision() {
        PaginatedResult.Pagination pagination = new PaginatedResult.Pagination(1, 20, 100);
        assertThat(pagination.getTotalPages()).isEqualTo(5);
    }

    @Test
    void testPagination_calculatesPages_withRemainder() {
        PaginatedResult.Pagination pagination = new PaginatedResult.Pagination(1, 20, 95);
        assertThat(pagination.getTotalPages()).isEqualTo(5);
    }

    @Test
    void testPagination_calculatesPages_singlePage() {
        PaginatedResult.Pagination pagination = new PaginatedResult.Pagination(1, 20, 10);
        assertThat(pagination.getTotalPages()).isEqualTo(1);
    }

    @Test
    void testPagination_calculatesPages_emptyResult() {
        PaginatedResult.Pagination pagination = new PaginatedResult.Pagination(1, 20, 0);
        assertThat(pagination.getTotalPages()).isEqualTo(0);
    }

    @Test
    void testPagination_settersAndGetters() {
        PaginatedResult.Pagination pagination = new PaginatedResult.Pagination(1, 20, 100);

        pagination.setPage(2);
        assertThat(pagination.getPage()).isEqualTo(2);

        pagination.setPageSize(50);
        assertThat(pagination.getPageSize()).isEqualTo(50);

        pagination.setTotalRecords(200);
        assertThat(pagination.getTotalRecords()).isEqualTo(200);

        pagination.setTotalPages(10);
        assertThat(pagination.getTotalPages()).isEqualTo(10);
    }

    @Test
    void testPaginatedResult_withEmptyData() {
        List<Map<String, Object>> data = new ArrayList<>();
        PaginatedResult.Pagination pagination = new PaginatedResult.Pagination(1, 20, 0);
        PaginatedResult result = new PaginatedResult(data, pagination);

        assertThat(result.getData()).isEmpty();
        assertThat(result.getPagination().getTotalRecords()).isEqualTo(0);
        assertThat(result.getPagination().getTotalPages()).isEqualTo(0);
    }

    @Test
    void testPaginatedResult_withMultipleRows() {
        List<Map<String, Object>> data = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            Map<String, Object> row = new HashMap<>();
            row.put("id", i);
            row.put("value", "item-" + i);
            data.add(row);
        }

        PaginatedResult.Pagination pagination = new PaginatedResult.Pagination(1, 20, 100);
        PaginatedResult result = new PaginatedResult(data, pagination);

        assertThat(result.getData()).hasSize(20);
        assertThat(result.getData().get(0).get("id")).isEqualTo(1);
        assertThat(result.getData().get(19).get("id")).isEqualTo(20);
    }
}
