package net.java21.data2flow.contracts.test.arch.sample;

/** 조직 ID를 담은 검색 조건(타입으로 조직 조건을 넘기는 경우) */
public record DeviceSearch(long organizationId, String keyword) {
}
