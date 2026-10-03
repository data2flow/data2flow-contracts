package net.java21.data2flow.contracts.connector;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 연결 테스트 결과(DSC-09.11, API-DSC-51 응답과 같은 모양).
 *
 * @param steps        단계별 결과(DNS → TCP → TLS → AUTH → SUBSCRIBE 순서, 해당 없는 단계는 SKIPPED)
 * @param preview      첫 메시지 미리보기(최대 {@value #MAX_PREVIEW}건)
 * @param lossPossible 이 설정으로는 유실될 수 있음(QoS 0 등)
 */
public record ConnectionTestResult(List<Step> steps, List<Preview> preview, boolean lossPossible) {

    public static final int MAX_PREVIEW = 10;
    public static final String STEP_DNS = "DNS";
    public static final String STEP_TCP = "TCP";
    public static final String STEP_TLS = "TLS";
    public static final String STEP_AUTH = "AUTH";
    public static final String STEP_SUBSCRIBE = "SUBSCRIBE";

    public ConnectionTestResult {
        steps = steps == null ? List.of() : List.copyOf(steps);
        preview = preview == null ? List.of() : List.copyOf(preview);
        if (preview.size() > MAX_PREVIEW) {
            preview = preview.subList(0, MAX_PREVIEW);
        }
    }

    /** 모든 단계가 실패 없이 끝났는가 */
    @JsonIgnore
    public boolean succeeded() {
        return !steps.isEmpty() && failedStep().isEmpty();
    }

    /** 처음 실패한 단계 */
    public Optional<Step> failedStep() {
        return steps.stream().filter(s -> s.status() == StepStatus.FAILED).findFirst();
    }

    public enum StepStatus {
        OK, FAILED, SKIPPED
    }

    /**
     * @param name     단계 이름({@code DNS}·{@code TCP}·{@code TLS}·{@code AUTH}·{@code SUBSCRIBE})
     * @param status   결과
     * @param ms       걸린 시간(밀리초)
     * @param code     실패 코드(예: {@code TLS_CERT_CHAIN}). 성공이면 null
     * @param detail   설명(비밀값 없음). 없으면 null
     * @param tlsChain TLS 단계의 인증서 체인 요약. 없으면 null
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Step(String name, StepStatus status, long ms, String code, String detail, List<String> tlsChain) {

        public static Step ok(String name, long ms) {
            return new Step(name, StepStatus.OK, ms, null, null, null);
        }

        public static Step failed(String name, long ms, String code, String detail) {
            return new Step(name, StepStatus.FAILED, ms, code, detail, null);
        }

        public static Step skipped(String name) {
            return new Step(name, StepStatus.SKIPPED, 0, null, null, null);
        }
    }

    /**
     * @param at         받은 시각
     * @param topic      토픽·경로. 없으면 null
     * @param size       원본 크기(바이트)
     * @param rawExcerpt 원본 앞부분(4KB 이하, 문자열)
     * @param decoded    소스 디코더로 해석한 결과. 해석하지 않았으면 null
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Preview(Instant at, String topic, int size, String rawExcerpt, JsonNode decoded) {

        public static final int MAX_EXCERPT_CHARS = 4096;

        public Preview {
            if (rawExcerpt != null && rawExcerpt.length() > MAX_EXCERPT_CHARS) {
                rawExcerpt = rawExcerpt.substring(0, MAX_EXCERPT_CHARS);
            }
        }
    }
}
