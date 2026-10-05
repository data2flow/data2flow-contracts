package net.java21.data2flow.contracts.message.event;

/** analytics(Python) 이벤트의 문자열 ID를 숫자로 바꾼다(API 규칙: ID는 JSON 문자열) */
final class AnalyticsIds {

    private AnalyticsIds() {
    }

    static Long toLong(String id) {
        if (id == null || !id.matches("\\d{1,19}")) {
            return null;
        }
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
