package net.java21.data2flow.contracts.output;

import com.fasterxml.jackson.annotation.JsonInclude;
import net.java21.data2flow.contracts.message.CanonicalTelemetry;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 출력 연결 필터(output_connections.filter, DSC-04.01 AT-DSC-10.1 "co2만 전달"). 빈 목록은 "모두"다. 기기·그룹·공간 조건은
 * 하나라도 맞으면 통과(OR)하고, 측정 항목과 최소 품질은 측정값마다 거른다.
 *
 * @param deviceIds  기기
 * @param groupIds   그룹(기기의 그룹 소속은 호출자가 넘긴다)
 * @param spaceIds   공간(하위 공간을 포함하려면 호출자가 기기 공간 경로를 넘긴다)
 * @param metrics    측정 항목 키
 * @param qualityMin 이 품질 코드 이하만 보낸다(0 = 정상만). null이면 모두
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OutputFilter(List<Long> deviceIds, List<Long> groupIds, List<Long> spaceIds, List<String> metrics,
                           Integer qualityMin) {

    public OutputFilter {
        deviceIds = deviceIds == null ? List.of() : List.copyOf(deviceIds);
        groupIds = groupIds == null ? List.of() : List.copyOf(groupIds);
        spaceIds = spaceIds == null ? List.of() : List.copyOf(spaceIds);
        metrics = metrics == null ? List.of() : List.copyOf(metrics);
        if (qualityMin != null && (qualityMin < 0 || qualityMin > 5)) {
            throw new IllegalArgumentException("qualityMin은 0~5입니다");
        }
    }

    /** 모두 통과 */
    public static OutputFilter all() {
        return new OutputFilter(null, null, null, null, null);
    }

    /**
     * 텔레메트리 하나를 거른다. 대상이 아니거나 남는 측정값이 없으면 빈 값, 아니면 걸러진 측정값만 남긴 사본.
     *
     * @param deviceGroupIds 기기가 속한 그룹
     * @param spacePathIds   기기 공간의 루트부터 자기까지 ID(하위 공간 포함 판정). 공간이 없으면 빈 목록
     */
    public Optional<CanonicalTelemetry> select(CanonicalTelemetry t, Set<Long> deviceGroupIds, List<Long> spacePathIds) {
        if (!targets(t.deviceId(), deviceGroupIds, spacePathIds)) {
            return Optional.empty();
        }
        List<CanonicalTelemetry.Metric> kept = t.metrics().stream()
                .filter(m -> metrics.isEmpty() || metrics.contains(m.key()))
                .filter(m -> qualityMin == null || m.quality() <= qualityMin)
                .toList();
        if (kept.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(kept.size() == t.metrics().size() ? t : t.withMetrics(kept));
    }

    /** 기기가 대상인가(기기·그룹·공간 조건 중 하나, 조건이 모두 비면 모두) */
    public boolean targets(long deviceId, Set<Long> deviceGroupIds, List<Long> spacePathIds) {
        if (deviceIds.isEmpty() && groupIds.isEmpty() && spaceIds.isEmpty()) {
            return true;
        }
        if (deviceIds.contains(deviceId)) {
            return true;
        }
        if (deviceGroupIds != null && groupIds.stream().anyMatch(deviceGroupIds::contains)) {
            return true;
        }
        return spacePathIds != null && spaceIds.stream().anyMatch(spacePathIds::contains);
    }
}
