package net.java21.data2flow.contracts.message.event;

/**
 * EVT-DSC-06 {@code credential.revoked}: 기기 MQTT 자격증명 폐기(생산 core-api → 소비 ingress: 플랫폼 브로커 관리 API로 그 client
 * 연결 종료).
 *
 * @param deviceId     기기
 * @param credentialId 자격증명 ID
 * @param username     브로커 사용자 이름
 */
public record CredentialRevoked(long deviceId, long credentialId, String username) implements EventPayload {
}
