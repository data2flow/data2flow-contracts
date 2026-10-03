package net.java21.data2flow.contracts.message.decoder;

import net.java21.data2flow.contracts.message.CanonicalTelemetry;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 디코더 출력(SCR-api §3.1 {@code DecodeOutput}과 같은 모양). 아직 기기 ID·별칭 변환·품질 판정 전이다.
 *
 * @param externalId 기기 외부 ID(devEui 등), 1~128자. 없으면 디코더가 {@link DecodeException}을 던진다
 * @param measuredAt 측정 시각. payload에 없으면 null(pipeline이 receivedAt을 쓰고 보정한다, BR-ING-04)
 * @param values     측정값(원본 키)
 * @param link       통신 품질. 없으면 null
 * @param tags       소스가 준 태그(ChirpStack tags 등)
 */
public record DecodedUplink(String externalId, Instant measuredAt, List<DecodedValue> values,
                            CanonicalTelemetry.Link link, Map<String, String> tags) {

    public static final int MAX_EXTERNAL_ID_LENGTH = 128;

    public DecodedUplink {
        if (externalId == null || externalId.isBlank() || externalId.length() > MAX_EXTERNAL_ID_LENGTH) {
            throw new IllegalArgumentException("externalId는 1~" + MAX_EXTERNAL_ID_LENGTH + "자입니다");
        }
        values = values == null ? List.of() : List.copyOf(values);
        tags = tags == null ? Map.of() : Map.copyOf(tags);
    }
}
