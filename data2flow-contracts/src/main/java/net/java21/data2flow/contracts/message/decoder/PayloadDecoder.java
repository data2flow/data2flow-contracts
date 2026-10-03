package net.java21.data2flow.contracts.message.decoder;

import net.java21.data2flow.contracts.message.RawEnvelope;
import tools.jackson.databind.JsonNode;

/**
 * 디코더 SPI(ING-02.01). 원본 하나({@link RawEnvelope})를 기기 식별 전 표준 형태({@link DecodedUplink}) 하나로 바꾼다.
 *
 * <p>pipeline의 디코더 레지스트리가 소스 설정 {@code decoder}로 고른다: 기본 제공 {@code chirpstack-v4}·{@code generic-json}·
 * {@code single-value}, 사용자 DECODE 스크립트 {@code script:{id}@v{n}}({@link DecoderKeys}). 그 뒤 단계(별칭 → 기기 식별 →
 * TRANSFORM → 검증 → 저장)를 거쳐 {@link net.java21.data2flow.contracts.message.CanonicalTelemetry}가 된다.
 *
 * <p>구현은 상태가 없고 스레드 안전해야 한다. 원본을 해석할 수 없으면 {@link DecodeException}을 던진다(처리 상태
 * {@code DECODE_ERROR}, 결과 코드 {@code ING_DECODE_FAILED}). DECODE는 원본 해석이 불가능한 상황이므로 fail-open이 없다.
 */
public interface PayloadDecoder {

    /** 디코더 키(예: {@code chirpstack-v4}). 처리 기록 {@code meta.decoder.key}에 남는다 */
    String key();

    /** 디코더 버전. 재처리는 시작 시점 버전을 고정한다(BR-ING-12) */
    String version();

    /**
     * @param raw    원본
     * @param config 소스의 디코더 설정({@code data_sources.config.decoder}). 설정이 없으면 빈 객체
     */
    DecodedUplink decode(RawEnvelope raw, JsonNode config) throws DecodeException;
}
