package net.java21.data2flow.contracts.message.event;

import com.fasterxml.jackson.annotation.JsonInclude;
import net.java21.data2flow.contracts.command.CommandPriority;
import net.java21.data2flow.contracts.command.CommandSource;
import net.java21.data2flow.contracts.command.CommandStatus;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * EVT-ACT-01 {@code command.status.{status}}: 명령 상태가 바뀌었다(생산 action). 소비: core-api(SSE·이력), flow-engine(awaitResult
 * 제어 노드의 ok/failed 포트), data2flow-ai. 라우팅 키는 {@link CommandStatus#routingKey()}.
 *
 * @param commandId      명령 ID
 * @param idempotencyKey 멱등 키(플로우는 BR-FLW-13 키라 노드가 자기 행동의 결과를 찾는다)
 * @param deviceId       기기 ID
 * @param spaceId        기기의 공간 ID. 없으면 null
 * @param capability     기능
 * @param command        명령
 * @param args           인자
 * @param status         새 상태
 * @param reason         사유(CommandStatusReasons). 없으면 null
 * @param message        사용자에게 보일 차단 사유(인터락 message 등). 없으면 null
 * @param source         출처
 * @param priority       우선순위
 * @param at             상태가 바뀐 시각
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CommandStatusChanged(UUID commandId, String idempotencyKey, long deviceId, Long spaceId, String capability,
                                   String command, Map<String, Object> args, CommandStatus status, String reason,
                                   String message, CommandSource source, CommandPriority priority, Instant at)
        implements EventPayload {
}
