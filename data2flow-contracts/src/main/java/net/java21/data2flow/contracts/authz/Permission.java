package net.java21.data2flow.contracts.authz;

/**
 * 서버가 확인하는 권한 이름(spec/IAM-identity.md "Permission 목록", IAM-04.01). 권한표의 "영역 × 행위"를 하나씩 나타낸다.
 * API 문서의 "권한:" 줄, 사용자 정의 역할(IAM-04.03), 권한 매트릭스 테스트(IAM-04.05)는 모두 이 이름을 쓴다.
 * 표의 "다른 표기"(DEVICE_WRITE, ACT_EXECUTE, FLOW_DEPLOY 등)는 enum에 두지 않는다.
 *
 * <p>제어 권한은 따로 둔다(IAM-04.04, BR-IAM-17): 기기 수동 제어 {@link #DEVICE_CONTROL}, 제어·장면 노드를 포함한
 * 플로우 적용 {@link #FLOW_DEPLOY_CONTROL}. 제어 노드가 없는 플로우 적용은 {@link #FLOW_WRITE}다.
 */
public enum Permission {
    // IAM
    IAM_MANAGE("IAM", false),
    AUDIT_READ("IAM", false),
    API_TOKEN_ISSUE("IAM"),
    // DSC
    SRC_READ("DSC"),
    SRC_ADMIN("DSC"),
    // SCR
    SCRIPT_READ("SCR"),
    SCRIPT_WRITE("SCR"),
    // ING
    INGEST_READ("ING"),
    INGEST_PAYLOAD_READ("ING"),
    INGEST_REPROCESS("ING"),
    // DEV
    DEV_READ("DEV"),
    DEV_PLACE("DEV"),
    DEV_ADMIN("DEV"),
    WORKORDER_WRITE("DEV"),
    // ACT
    DEVICE_CONTROL("ACT"),
    SCENE_RUN("ACT"),
    SCENE_MANAGE("ACT"),
    SCHEDULE_MANAGE("ACT"),
    CONTROL_SETTINGS("ACT"),
    INTERLOCK_MANAGE("ACT"),
    DRIVER_MANAGE("ACT"),
    CAPABILITY_MANAGE("ACT"),
    EMERGENCY_STOP("ACT"),
    EMERGENCY_RELEASE("ACT"),
    // FLW
    FLOW_READ("FLW"),
    FLOW_WRITE("FLW"),
    FLOW_DEPLOY_CONTROL("FLW"),
    FLOW_APPROVE("FLW"),
    SINK_CONNECTION_MANAGE("FLW"),
    GIT_SYNC_MANAGE("FLW"),
    NODE_PACKAGE_MANAGE("FLW"),
    // RUL
    RULE_READ("RUL"),
    RULE_WRITE("RUL"),
    ALARM_READ("RUL"),
    ALARM_HANDLE("RUL"),
    NOTIFY_POLICY_WRITE("RUL"),
    NOTIFY_CHANNEL_MANAGE("RUL"),
    // ANA
    ANALYTICS_READ("ANA"),
    ANALYTICS_RUN("ANA"),
    // AIA
    AI_USE("AIA"),
    // SIM
    SIM_READ("SIM"),
    SIM_RUN("SIM"),
    SIM_MANAGE("SIM"),
    SIM_ADMIN("SIM"),
    // DSH
    DASHBOARD_READ("DSH"),
    DASHBOARD_WRITE("DSH"),
    BRANDING_MANAGE("DSH"),
    // OPS
    OPS_MANAGE("OPS"),
    // TSD
    TS_READ("TSD"),
    TS_EXPORT("TSD"),
    TS_IMPORT("TSD"),
    TS_POLICY("TSD"),
    // CMP
    CMP_READ("CMP"),
    CMP_REPORT("CMP"),
    CMP_ACTION("CMP"),
    CMP_ADMIN("CMP"),
    // OCC
    OCC_READ("OCC"),
    OCC_MANAGE("OCC"),
    BOOKING_READ("OCC"),
    BOOKING_ADMIN("OCC"),
    // ENE
    ENE_READ("ENE"),
    ENE_OPERATE("ENE"),
    ENE_ANALYZE("ENE"),
    ENE_ADMIN("ENE");

    private final String area;
    private final boolean customRoleAllowed;

    Permission(String area) {
        this(area, true);
    }

    Permission(String area, boolean customRoleAllowed) {
        this.area = area;
        this.customRoleAllowed = customRoleAllowed;
    }

    /** 권한표 영역(스펙 도메인 코드). API-IAM-73 응답의 {@code area} */
    public String area() {
        return area;
    }

    /** 사용자 정의 역할에 넣을 수 있는가. IAM_MANAGE·AUDIT_READ는 ADMIN 전용이다(BR-IAM-30) */
    public boolean customRoleAllowed() {
        return customRoleAllowed;
    }
}
