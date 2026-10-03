package net.java21.data2flow.contracts.message;

/**
 * 플랫폼 브로커 기기 payload 서명 검증 결과({@link RawEnvelope#signatureStatus()}, DSC-03.02·03.03·03.05, ADR-042).
 * 문자열 상수라서 생산자가 새 값을 더해도 소비자가 깨지지 않는다(모르는 값은 {@link #VERIFIED}가 아닌 것으로 본다).
 *
 * <ul>
 *   <li>{@link #VERIFIED}: 기기의 ACTIVE 서명 키로 계산한 HMAC이 맞다. payload는 서명 접두사를 뗀 본문이다</li>
 *   <li>{@link #UNSIGNED}: ingress가 이 기기의 서명 키를 모른다(승인 전·키 폐기). 서명 접두사가 있으면 떼어 낸 본문이다.
 *       pipeline은 승인 대기 기기면 quality 2로 격리하고, 승인된 기기면 {@code DEVICE_SIGNATURE_INVALID}로 거부한다</li>
 *   <li>{@link #INVALID}: 서명 키가 있는데 서명이 없거나 틀렸다. payload는 받은 그대로이고 pipeline은 원본만 거부 상태로 남긴다</li>
 * </ul>
 */
public final class SignatureStatus {

    public static final String VERIFIED = "VERIFIED";
    public static final String UNSIGNED = "UNSIGNED";
    public static final String INVALID = "INVALID";

    /** 거부 오류 코드(00-error-codes.md) */
    public static final String ERROR_CODE = "DEVICE_SIGNATURE_INVALID";

    private SignatureStatus() {
    }
}
