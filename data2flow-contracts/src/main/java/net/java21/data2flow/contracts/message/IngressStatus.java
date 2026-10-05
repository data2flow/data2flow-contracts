package net.java21.data2flow.contracts.message;

/**
 * ingress가 원본 봉투에 붙이는 미처리 판정({@link RawEnvelope#ingressStatus()})과 토픽 템플릿 예약 키
 * ({@link RawEnvelope#topicAttributes()}, DSC-09.07·09.08, BR-DSC-28). 문자열 상수라서 생산자가 값을 더해도 소비자가 깨지지 않는다.
 *
 * <ul>
 *   <li>{@link #DECODE_ERROR}: 소스에 지정한 형식(CBOR·Protobuf·Avro 등)이나 압축으로 payload를 풀 수 없었다. payload는 받은 그대로다</li>
 *   <li>{@link #UNMATCHED_TOPIC}: 토픽·경로가 소스의 토픽 템플릿에 맞지 않는다. payload는 받은 그대로이고 형식 변환도 하지 않았다</li>
 * </ul>
 * pipeline은 두 경우 모두 디코딩하지 않고 원본만 그 상태(오류 코드)로 남기며 소스 지표의 미처리 건수에 센다.
 */
public final class IngressStatus {

    public static final String DECODE_ERROR = "DECODE_ERROR";
    public static final String UNMATCHED_TOPIC = "UNMATCHED_TOPIC";

    /** 토픽 템플릿의 기기 ID({@code {deviceId}}·{@code {externalId}}·{@code {deviceKey}}·{@code {devEui}} 변수) */
    public static final String ATTR_EXTERNAL_ID = "externalId";
    /** 토픽 템플릿의 측정 항목 힌트({@code {metric}} 변수) */
    public static final String ATTR_METRIC = "metric";
    /** 공간 힌트: 공간 변수(site·building·floor·room·space·zone·area)를 템플릿 순서대로 {@code /}로 이은 값(예: {@code a/301}) */
    public static final String ATTR_SPACE_HINT = "spaceHint";

    private IngressStatus() {
    }
}
