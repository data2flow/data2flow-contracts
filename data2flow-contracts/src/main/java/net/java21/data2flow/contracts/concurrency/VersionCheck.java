package net.java21.data2flow.contracts.concurrency;

import net.java21.data2flow.contracts.error.BusinessException;
import net.java21.data2flow.contracts.error.CommonErrorCode;
import net.java21.data2flow.contracts.error.ErrorCode;
import net.java21.data2flow.contracts.error.FieldErrorDetail;
import tools.jackson.databind.JsonNode;

import java.util.List;

/**
 * 낙관적 잠금(OPS-12.04, BR-OPS-21, api-rules §5). 수정 요청 본문의 {@code baseVersion}이 현재 {@code version}과 다르면
 * 409 {@code VERSION_CONFLICT}(또는 도메인 전용 코드, 예: FLOW_VERSION_CONFLICT)이고 데이터는 바뀌지 않는다.
 * 성공하면 저장할 때 version을 1 올린다. ETag·If-Match는 쓰지 않는다.
 *
 * <p>두 가지 방법을 함께 쓴다.
 * <ol>
 *   <li>읽은 뒤 비교: {@link #require(long, long)} — 빠른 실패와 명확한 오류.</li>
 *   <li>쓰기 조건: {@code UPDATE … SET version = version + 1 WHERE id = ? AND version = ?}의 결과 행 수를
 *       {@link #requireUpdated(int)}로 확인 — 읽기와 쓰기 사이의 경쟁까지 막는다. JPA {@code @Version} 충돌은
 *       공통 예외 처리기가 409 VERSION_CONFLICT로 바꾼다.</li>
 * </ol>
 */
public final class VersionCheck {

    public static final String BASE_VERSION = "baseVersion";

    private VersionCheck() {
    }

    public static void require(long baseVersion, long currentVersion) {
        require(baseVersion, currentVersion, CommonErrorCode.VERSION_CONFLICT);
    }

    public static void require(long baseVersion, long currentVersion, ErrorCode conflictCode) {
        if (baseVersion != currentVersion) {
            throw new BusinessException(conflictCode);
        }
    }

    /** 조건부 UPDATE가 한 행도 바꾸지 못했으면 다른 사용자가 먼저 바꾼 것이다 */
    public static void requireUpdated(int updatedRows) {
        requireUpdated(updatedRows, CommonErrorCode.VERSION_CONFLICT);
    }

    public static void requireUpdated(int updatedRows, ErrorCode conflictCode) {
        if (updatedRows == 0) {
            throw new BusinessException(conflictCode);
        }
    }

    /**
     * PATCH 본문(JsonNode, api-rules §2)에서 {@code baseVersion}을 꺼낸다. 없거나 0 이상의 정수가 아니면
     * 400 INVALID_REQUEST(errors[0].field=baseVersion).
     */
    public static long baseVersion(JsonNode body) {
        JsonNode node = body == null ? null : body.get(BASE_VERSION);
        if (node == null || node.isNull()) {
            throw invalid("NotNull");
        }
        if (!node.isIntegralNumber() || node.asLong() < 0) {
            throw invalid("Min");
        }
        return node.asLong();
    }

    private static BusinessException invalid(String code) {
        return new BusinessException(CommonErrorCode.INVALID_REQUEST, List.of(new FieldErrorDetail(BASE_VERSION, code, null)));
    }
}
