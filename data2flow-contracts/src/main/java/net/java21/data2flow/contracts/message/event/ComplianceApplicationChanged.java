package net.java21.data2flow.contracts.message.event;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;

import java.util.List;

/**
 * EVT-CMP-01 {@code compliance.application.changed}: 기준 프로필 적용이 바뀌었다(CMP-01.03, 생산 core-api → 소비 flow-engine 유효 기준
 * 캐시 무효화, core-api SSE). 공간의 유효 기준(BR-CMP-04)은 이 공간들에서 다시 계산해야 한다.
 *
 * @param applicationId 적용 ID
 * @param profileId     프로필 ID
 * @param versionNo     이 적용이 지금 쓰는 프로필 버전(고정 버전 또는 최신)
 * @param spaceIds      영향받는 공간 ID(하위 포함이면 펼친 목록, 공간 용도 적용이면 그 용도의 공간들)
 * @param change        CREATED, DELETED, VERSION_ADVANCED(최신을 따르는 적용에 새 버전이 생김)
 */
public record ComplianceApplicationChanged(long applicationId, long profileId, int versionNo, List<Long> spaceIds, Change change)
        implements EventPayload {

    public ComplianceApplicationChanged {
        if (change == null || versionNo < 1) {
            throw new IllegalArgumentException("change는 필수이고 versionNo는 1 이상입니다");
        }
        spaceIds = spaceIds == null ? List.of() : List.copyOf(spaceIds);
    }

    /** 적용 변경 종류. 모르는 값은 UNKNOWN(하위 호환) */
    public enum Change {
        CREATED, DELETED, VERSION_ADVANCED,
        @JsonEnumDefaultValue
        UNKNOWN
    }
}
