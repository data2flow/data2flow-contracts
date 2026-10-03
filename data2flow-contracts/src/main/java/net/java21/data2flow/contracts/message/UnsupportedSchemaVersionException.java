package net.java21.data2flow.contracts.message;

/**
 * 받은 메시지의 스키마 버전 {@code v}를 이 코드가 모른다(없거나, 0 이하이거나, 아는 최신 버전보다 크다).
 *
 * <p>생산자가 먼저 배포되어 새 버전을 보냈다는 뜻이다. 소비자는 메시지를 버리지 말고 DLQ로 보내 배포 후 재처리한다.
 */
public class UnsupportedSchemaVersionException extends MessageFormatException {

    private final String schema;
    private final Integer receivedVersion;
    private final int supportedVersion;

    public UnsupportedSchemaVersionException(String schema, Integer receivedVersion, int supportedVersion) {
        super("지원하지 않는 메시지 스키마 버전입니다: " + schema + " v=" + receivedVersion + " (지원: 1~" + supportedVersion + ")");
        this.schema = schema;
        this.receivedVersion = receivedVersion;
        this.supportedVersion = supportedVersion;
    }

    public String schema() {
        return schema;
    }

    /** 받은 버전. 필드가 없거나 정수가 아니면 null */
    public Integer receivedVersion() {
        return receivedVersion;
    }

    public int supportedVersion() {
        return supportedVersion;
    }
}
