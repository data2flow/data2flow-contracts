package net.java21.data2flow.contracts.web;

/** 모든 응답의 머리 {@code {isSuccessful, resultCode, resultMessage}} (crowfoot 공통 포맷, ADR-035) */
public record ApiHeader(boolean isSuccessful, String resultCode, String resultMessage) {

    public static final String SUCCESS = "SUCCESS";

    public static ApiHeader success() {
        return new ApiHeader(true, SUCCESS, "SUCCESS");
    }

    public static ApiHeader failure(String resultCode, String resultMessage) {
        return new ApiHeader(false, resultCode, resultMessage);
    }
}
