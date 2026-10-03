package net.java21.data2flow.contracts.command;

/**
 * 명령 상태 사유(commands.status_reason varchar(32), ACT domain-model §2·§3). 목록은 열려 있어 문자열 상수로 둔다.
 * SUPERSEDED는 사유가 아니라 상태다.
 */
public final class CommandStatusReasons {

    public static final String EXPIRED = "EXPIRED";
    public static final String SANDBOX_FORBIDDEN = "SANDBOX_FORBIDDEN";
    public static final String DRIVER_UNAVAILABLE = "DRIVER_UNAVAILABLE";
    public static final String INTERLOCK = "INTERLOCK";
    public static final String PROTECTION = "PROTECTION";
    public static final String MANUAL_OVERRIDE = "MANUAL_OVERRIDE";
    public static final String EMERGENCY_STOP = "EMERGENCY_STOP";
    public static final String OSCILLATION = "OSCILLATION";
    public static final String NO_CHANGE = "NO_CHANGE";
    public static final String TIMEOUT_ACK = "TIMEOUT_ACK";
    public static final String TIMEOUT_APPLY = "TIMEOUT_APPLY";
    public static final String DRIVER_ERROR = "DRIVER_ERROR";
    /** 검증 단계 거부(ArgViolation.Reason과 같은 이름) */
    public static final String CAPABILITY_NOT_SUPPORTED = "CAPABILITY_NOT_SUPPORTED";
    public static final String ARGS_INVALID = "ARGS_INVALID";
    public static final String MODEL_CONSTRAINT = "MODEL_CONSTRAINT";
    public static final String ABSOLUTE_LIMIT = "ABSOLUTE_LIMIT";
    public static final String RATE_LIMITED = "RATE_LIMITED";
    /** 가상 장비가 처리 시점 상태로 적용할 수 없음(EVT-SIM-03) */
    public static final String INVALID_COMMAND = "INVALID_COMMAND";
    public static final String DEVICE_NOT_SIMULATED = "DEVICE_NOT_SIMULATED";

    /** status_reason 열 길이 */
    public static final int MAX_LENGTH = 32;

    private CommandStatusReasons() {
    }
}
