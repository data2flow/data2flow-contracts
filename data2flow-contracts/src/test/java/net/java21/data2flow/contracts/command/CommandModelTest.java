package net.java21.data2flow.contracts.command;

import net.java21.data2flow.contracts.message.MessageCodec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** ACT-02.02·02.03·02.05: 명령 상태·출처·우선순위, 행동 멱등 키(BR-ACT-02·15·24, BR-FLW-13) */
class CommandModelTest {

    private final JsonMapper mapper = MessageCodec.newMapper();

    @ParameterizedTest
    @CsvSource({"USER, MANUAL", "BULK, MANUAL", "SYSTEM, SAFETY", "SCHEDULE, SCHEDULE", "FLOW, AUTO", "RULE, AUTO", "AI, AI"})
    @DisplayName("ACT-02.03 TC-ACT-040 BR-ACT-24 우선순위는 출처가 정한다")
    void priorityBySource(SourceType source, CommandPriority expected) {
        assertThat(CommandPriority.forSource(source)).isEqualTo(expected);
        assertThat(CommandPriority.forScene(source)).isEqualTo(expected);
    }

    @Test
    @DisplayName("ACT-02.05 BR-ACT-24 MANUAL > SAFETY > SCHEDULE > AUTO = AI, 자동 우선순위만 비상 정지·최소 간격 대상")
    void priorityOrder() {
        assertThat(CommandPriority.MANUAL.outranks(CommandPriority.SAFETY)).isTrue();
        assertThat(CommandPriority.SAFETY.outranks(CommandPriority.SCHEDULE)).isTrue();
        assertThat(CommandPriority.SCHEDULE.outranks(CommandPriority.AUTO)).isTrue();
        assertThat(CommandPriority.AUTO.outranks(CommandPriority.AI)).isFalse();
        assertThat(CommandPriority.AI.outranks(CommandPriority.AUTO)).isFalse();
        assertThat(CommandPriority.AUTO.rank()).isEqualTo(CommandPriority.AI.rank());
        assertThat(CommandPriority.MANUAL.automatic()).isFalse();
        assertThat(CommandPriority.SAFETY.automatic()).isFalse();
        assertThat(CommandPriority.SCHEDULE.automatic()).isTrue();
        assertThat(CommandPriority.AUTO.automatic()).isTrue();
        assertThat(CommandPriority.AI.automatic()).isTrue();
        assertThatThrownBy(() -> CommandPriority.forSource(SourceType.SCENE)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CommandPriority.forSource(SourceType.UNKNOWN)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CommandPriority.forScene(SourceType.SCENE)).isInstanceOf(IllegalArgumentException.class);
        assertThat(mapper.readValue("\"URGENT\"", CommandPriority.class)).isEqualTo(CommandPriority.UNKNOWN);
    }

    @Test
    @DisplayName("ACT-02.03 TC-ACT-040 BR-ACT-15 출처 기록: 종류별 필수 칸, AI는 승인자가 있어야 한다")
    void sourceCompleteness() {
        assertThat(CommandSource.user(7).requireComplete().priority()).isEqualTo(CommandPriority.MANUAL);
        assertThat(CommandSource.flow("f-7f3a", 13, "n-act-1", "m-1").requireComplete().priority()).isEqualTo(CommandPriority.AUTO);
        assertThat(CommandSource.ai("s-1", 7).requireComplete().priority()).isEqualTo(CommandPriority.AI);
        assertThat(CommandSource.schedule(3).requireComplete().priority()).isEqualTo(CommandPriority.SCHEDULE);
        assertThat(CommandSource.bulk(7, "b-1").requireComplete().priority()).isEqualTo(CommandPriority.MANUAL);
        assertThat(CommandSource.system().requireComplete().priority()).isEqualTo(CommandPriority.SAFETY);
        CommandSource aiWithoutApproval = new CommandSource(SourceType.AI, null, null, null, null, null, null, null, null, "s-1", null);
        assertThatThrownBy(aiWithoutApproval::requireComplete).hasMessageContaining("AI");
        assertThatThrownBy(() -> new CommandSource(SourceType.FLOW, null, "f", 1, null, null, null, null, null, null, null)
                .requireComplete()).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CommandSource(SourceType.SCENE, 7L, null, null, null, null, null, null, null, null, null)
                .requireComplete()).isInstanceOf(IllegalArgumentException.class);
        assertThat(new CommandSource(SourceType.SCENE, 7L, null, null, null, null, "sr-1", null, null, null, null).requireComplete())
                .isNotNull();
        assertThat(new CommandSource(SourceType.RULE, null, null, null, null, null, null, null, null, null, null).requireComplete())
                .isNotNull();
        assertThatThrownBy(() -> new CommandSource(SourceType.UNKNOWN, null, null, null, null, null, null, null, null, null, null)
                .requireComplete()).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CommandSource(SourceType.USER, null, null, null, null, null, null, null, null, null, null)
                .requireComplete()).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CommandSource(null, 1L, null, null, null, null, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(mapper.writeValueAsString(CommandSource.user(7))).isEqualTo("{\"type\":\"USER\",\"userId\":7}");
    }

    @ParameterizedTest
    @EnumSource(value = CommandStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "UNKNOWN")
    @DisplayName("ACT-02.02 EVT-ACT-01 라우팅 키는 command.status.{소문자}, 끝 상태는 domain-model §3 목록")
    void statusRoutingAndTerminal(CommandStatus status) {
        assertThat(status.routingKey()).isEqualTo("command.status." + status.name().toLowerCase());
        boolean terminal = switch (status) {
            case APPLIED, FAILED, TIMEOUT, REJECTED, BLOCKED, SKIPPED, SUPERSEDED, CANCELLED -> true;
            default -> false;
        };
        assertThat(status.terminal()).isEqualTo(terminal);
        assertThat(status.cancellable()).isEqualTo(status == CommandStatus.QUEUED || status == CommandStatus.DELAYED
                || status == CommandStatus.QUEUED_FOR_DOWNLINK);
        assertThat(status.succeeded()).isEqualTo(status == CommandStatus.APPLIED);
    }

    @Test
    @DisplayName("ACT-02.02 모르는 상태는 UNKNOWN으로 읽고 발행할 수 없다")
    void unknownStatus() {
        assertThat(mapper.readValue("\"VAPORIZED\"", CommandStatus.class)).isEqualTo(CommandStatus.UNKNOWN);
        assertThatThrownBy(CommandStatus.UNKNOWN::routingKey).isInstanceOf(IllegalStateException.class);
        assertThat(CommandStatusReasons.SANDBOX_FORBIDDEN.length()).isLessThanOrEqualTo(CommandStatusReasons.MAX_LENGTH);
    }

    @Test
    @DisplayName("FLW-05.02 BR-FLW-13 플로우 멱등 키는 sha256(flowId, nodeId, triggerMessageId)이고 버전과 무관하다")
    void flowIdempotencyKey() {
        String key = ActionIdempotencyKeys.flow("f-7f3a", "n-act-1", "m-1");
        assertThat(key).hasSize(64).matches("[0-9a-f]{64}").isEqualTo(ActionIdempotencyKeys.flow("f-7f3a", "n-act-1", "m-1"));
        assertThat(ActionIdempotencyKeys.isValid(key)).isTrue();
        assertThat(key).isNotEqualTo(ActionIdempotencyKeys.flow("f-7f3a", "n-act-2", "m-1"))
                .isNotEqualTo(ActionIdempotencyKeys.flow("f-7f3a", "n-act-1", "m-2"))
                .isNotEqualTo(ActionIdempotencyKeys.flow("f-7f3a", "n-act-1", "m-1", 0));
        // 구분 문자가 있어 경계가 바뀐 입력은 다른 키다
        assertThat(ActionIdempotencyKeys.flow("ab", "c", "m")).isNotEqualTo(ActionIdempotencyKeys.flow("a", "bc", "m"));
        assertThat(ActionIdempotencyKeys.flow("f", "n", "m", 1)).isNotEqualTo(ActionIdempotencyKeys.flow("f", "n", "m", 2));
        assertThat(ActionIdempotencyKeys.of("schedule", "3", "2026-10-03T08:50:00Z")).hasSize(64)
                .isNotEqualTo(ActionIdempotencyKeys.of("schedule", "3", "2026-10-04T08:50:00Z"));
        assertThatThrownBy(() -> ActionIdempotencyKeys.flow("f", " ", "m")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ActionIdempotencyKeys.flow("f", "n", "m", -1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ActionIdempotencyKeys.of(null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(ActionIdempotencyKeys.isValid("ui-7d2c:click-0001")).isTrue();
        assertThat(ActionIdempotencyKeys.isValid("a b")).isFalse();
        assertThat(ActionIdempotencyKeys.isValid("x".repeat(65))).isFalse();
        assertThat(ActionIdempotencyKeys.isValid("")).isFalse();
        assertThat(ActionIdempotencyKeys.isValid(null)).isFalse();
    }

    @Test
    @DisplayName("ACT-02.01 TC-ACT-039 명령 대상은 기기 하나 또는 공간 관계(action이 기기별로 펼침)")
    void targets() {
        assertThat(CommandTarget.device(15).isDevice()).isTrue();
        CommandTarget space = CommandTarget.space(31, CommandTarget.RELATION_CONTROLS, "Thermostat", false);
        assertThat(space.isDevice()).isFalse();
        assertThat(mapper.writeValueAsString(CommandTarget.device(15))).isEqualTo("{\"deviceId\":15}");
        assertThatThrownBy(() -> new CommandTarget(15L, 31L, "controls", null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CommandTarget(null, null, null, null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CommandTarget(0L, null, null, null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CommandTarget(null, 31L, " ", null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CommandPayload(null, "Switch", "set", null, false)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CommandPayload(CommandTarget.device(1), "Switch", " ", null, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new CommandPayload(CommandTarget.device(1), "Switch", "set", null, false).args()).isEmpty();
    }

    @Test
    @DisplayName("ACT-02.01 ADR-020 행동 종류별 라우팅 키: COMMAND·SCENE → command, NOTIFY → notify, SINK → sink")
    void actionKinds() {
        assertThat(ActionKind.COMMAND.routingKey()).isEqualTo("command");
        assertThat(ActionKind.SCENE.routingKey()).isEqualTo("command");
        assertThat(ActionKind.NOTIFY.routingKey()).isEqualTo("notify");
        assertThat(ActionKind.SINK.routingKey()).isEqualTo("sink");
        assertThatThrownBy(ActionKind.UNKNOWN::routingKey).isInstanceOf(IllegalStateException.class);
        assertThat(mapper.readValue("\"WORK_ORDER\"", ActionKind.class)).isEqualTo(ActionKind.UNKNOWN);
        assertThat(mapper.readValue("\"TELEPORT\"", SourceType.class)).isEqualTo(SourceType.UNKNOWN);
    }
}
