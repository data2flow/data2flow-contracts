package net.java21.data2flow.contracts.connector;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * 커넥터 설명(DSC domain-model §6.1 {@code connector_catalogs}).
 *
 * @param key            커넥터 키(소문자·숫자·하이픈 40자 이하, 예: {@code mqtt}, {@code sparkplug-b})
 * @param name           표시 이름
 * @param version        커넥터 버전(semver). 소스는 저장 당시 버전을 기억한다
 * @param category       분류
 * @param authMethods    지원 인증(1개 이상)
 * @param payloadFormats 지원 형식(1개 이상)
 * @param ackMode        확인 방식
 * @param scaling        확장 방식
 * @param supportsSend   같은 연결로 제어 명령을 보낼 수 있는가(DSC-09.13)
 */
public record ConnectorDescriptor(String key, String name, String version, ConnectorCategory category,
                                  Set<AuthMethod> authMethods, Set<PayloadFormat> payloadFormats, AckMode ackMode,
                                  ScalingMode scaling, boolean supportsSend) {

    public static final Pattern KEY = Pattern.compile("[a-z0-9][a-z0-9-]{0,39}");
    public static final Pattern SEMVER = Pattern.compile("\\d+\\.\\d+\\.\\d+([-+][0-9A-Za-z.-]+)?");

    public ConnectorDescriptor {
        if (key == null || !KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("커넥터 키는 소문자·숫자·하이픈 40자 이하입니다: " + key);
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("커넥터 이름이 비어 있습니다: " + key);
        }
        if (version == null || !SEMVER.matcher(version).matches()) {
            throw new IllegalArgumentException("커넥터 버전은 semver입니다: " + version);
        }
        if (category == null || ackMode == null || scaling == null) {
            throw new IllegalArgumentException("category·ackMode·scaling은 필수입니다: " + key);
        }
        if (authMethods == null || authMethods.isEmpty() || payloadFormats == null || payloadFormats.isEmpty()) {
            throw new IllegalArgumentException("authMethods·payloadFormats는 1개 이상입니다: " + key);
        }
        authMethods = Set.copyOf(authMethods);
        payloadFormats = Set.copyOf(payloadFormats);
    }

    /** 화면 "유실 가능" 배지(DSC-09.03) */
    public boolean lossPossible() {
        return ackMode.lossPossible();
    }
}
