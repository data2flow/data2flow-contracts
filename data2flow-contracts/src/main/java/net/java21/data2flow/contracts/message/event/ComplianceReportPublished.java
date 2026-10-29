package net.java21.data2flow.contracts.message.event;

import java.time.LocalDate;
import java.util.List;

/**
 * EVT-CMP-03 {@code compliance.report.published}: 준수 리포트의 한 판을 발행했다(CMP-02.02·02.04, BR-CMP-15, 생산 core-api → 소비
 * data2flow-action 수신자 알림, 감사). 같은 조건으로 다시 발행하면 판 번호가 1 오른 새 이벤트가 생긴다.
 *
 * @param reportId   리포트 ID
 * @param revisionNo 판 번호(1부터)
 * @param type       리포트 유형
 * @param title      제목
 * @param periodFrom 대상 기간 시작일(조직 시간대, 포함)
 * @param periodTo   대상 기간 끝일(조직 시간대, 포함)
 * @param sha256     PDF 해시(소문자 16진수 64자, BR-CMP-16)
 * @param recipients 정기 발행 수신자(정기 발행이 아니면 null)
 */
public record ComplianceReportPublished(long reportId, int revisionNo, Type type, String title, LocalDate periodFrom, LocalDate periodTo,
                                        String sha256, Recipients recipients) implements EventPayload {

    public enum Type {
        KR_SELF_CHECK, RESET_SUPPORT, WELL_A08_SUPPORT
    }

    /** 정기 발행 수신자(API-CMP-21 recipients) */
    public record Recipients(List<Long> userIds, List<String> emails) {

        public Recipients {
            userIds = userIds == null ? List.of() : List.copyOf(userIds);
            emails = emails == null ? List.of() : List.copyOf(emails);
        }
    }

    public ComplianceReportPublished {
        if (revisionNo < 1 || type == null || title == null || periodFrom == null || periodTo == null) {
            throw new IllegalArgumentException("revisionNo는 1 이상이고 type·title·periodFrom·periodTo는 필수입니다");
        }
        if (sha256 == null || !sha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("sha256은 소문자 16진수 64자입니다");
        }
    }
}
