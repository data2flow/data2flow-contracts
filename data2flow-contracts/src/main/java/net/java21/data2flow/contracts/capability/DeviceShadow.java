package net.java21.data2flow.contracts.capability;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * 기기 상태 쌍(ACT-02.04, API-ACT-04, {@code data2flow_action.device_shadows}): 원하는 상태(desired)와 기기가 보고한 상태(reported).
 * 차이(delta)는 저장하지 않고 계산한다.
 *
 * @param desired         원하는 상태 {@code {capability:{attr:value}}}
 * @param desiredVersion  명령마다 1씩 오른다
 * @param reported        드라이버가 보고한 상태
 * @param reportedVersion 드라이버 시퀀스 또는 보고 시각 기반 버전. 아직 보고가 없으면 0
 * @param reportedAt      마지막 보고 시각. 없으면 null
 */
public record DeviceShadow(Map<String, Map<String, Object>> desired, long desiredVersion,
                           Map<String, Map<String, Object>> reported, long reportedVersion, Instant reportedAt) {

    public static final DeviceShadow EMPTY = new DeviceShadow(Map.of(), 0, Map.of(), 0, null);

    public DeviceShadow {
        desired = CapabilityStates.mergeAll(Map.of(), desired);
        reported = CapabilityStates.mergeAll(Map.of(), reported);
    }

    /** desired와 다른 reported 속성 */
    @JsonIgnore
    public Map<String, Map<String, Object>> delta() {
        return CapabilityStates.delta(desired, reported);
    }

    /** 원하는 상태와 실제가 같은지 */
    @JsonIgnore
    public boolean inSync() {
        return delta().isEmpty();
    }

    /** 명령을 받아 원하는 상태를 바꾼다(목표 상태 설정, BR-ACT-03). desiredVersion + 1 */
    public DeviceShadow withDesired(String capability, Map<String, ?> args) {
        return new DeviceShadow(CapabilityStates.merge(desired, capability, args), desiredVersion + 1, reported,
                reportedVersion, reportedAt);
    }

    /**
     * 상태 보고를 반영한다(BR-ACT-05). 버전이 지금보다 크지 않으면 버리고 빈 값을 돌려준다. 보고에 없는 기능·속성은 이전 값을 둔다.
     */
    public Optional<DeviceShadow> withReported(long version, Map<String, ? extends Map<String, ?>> capabilities, Instant at) {
        if (version <= reportedVersion) {
            return Optional.empty();
        }
        return Optional.of(new DeviceShadow(desired, desiredVersion, CapabilityStates.mergeAll(reported, capabilities),
                version, at));
    }

    /** 명령이 reported에 반영됐는지(APPLIED 판정) */
    public boolean isApplied(String capability, Map<String, ?> args) {
        return CapabilityStates.isApplied(reported, capability, args);
    }

    /** 명령을 보내도 바뀌는 것이 없는지(BR-ACT-04 SKIPPED(NO_CHANGE)): desired와 reported 모두 이미 목표와 같다 */
    public boolean noChange(String capability, Map<String, ?> args) {
        return CapabilityStates.isApplied(desired, capability, args) && CapabilityStates.isApplied(reported, capability, args);
    }
}
