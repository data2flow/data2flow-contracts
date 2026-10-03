package net.java21.data2flow.contracts.connector;

/** 커넥터가 지원하는 접속 인증(connector_catalogs.auth_methods) */
public enum AuthMethod {
    NONE, USER_PASSWORD, MTLS, TOKEN, WS_HEADER, OAUTH2_CC, MQTT5_ENHANCED,
    SASL_PLAIN, SASL_SCRAM_256, SASL_SCRAM_512, AWS_SIGV4
}
