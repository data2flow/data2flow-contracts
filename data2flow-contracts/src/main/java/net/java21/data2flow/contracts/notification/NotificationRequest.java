package net.java21.data2flow.contracts.notification;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import net.java21.data2flow.contracts.alarm.AlarmSeverity;

import java.util.List;
import java.util.Map;

/**
 * 알림 요청: {@code ActionRequest(kind=NOTIFY)}의 {@code payload}(EVT-RUL-03, ACT-api §5.1) → {@code action.notifications}.
 * 생산은 core-api(알람 정책 평가 후 아웃박스)와 flow-engine({@code action.notify} 노드, TC-FLW-055), 소비는 action {@code notification}
 * 패키지(채널과 무관한 공통 계층, BR-OPS-32)다.
 *
 * <pre>{@code
 * {"alarmId":9001,"event":"alarm.raised","eventSeq":1,"severity":"MAJOR",
 *  "recipients":[{"type":"ON_CALL","channel":"TELEGRAM"},{"type":"USER","id":"5","channel":"WEB"}],
 *  "templateKeys":{"TELEGRAM":"alarm.raised.default"},"variables":{"title":"실습실 고온","value":29.4},
 *  "aggregateWindowSec":60,"link":"https://data2flow.java21.net/alarms/9001"}
 * }</pre>
 *
 * <p>수신자를 정하는 쪽: 알람 알림은 core-api가 정책으로 수신자를 계산해 채운다(BR-RUL-12). 플로우 노드가 {@code policyId}만 주면
 * {@code recipients}가 비어 있고 action이 정책을 읽어 계산한다. 채널을 직접 지정하면(채널 지정 수신자) 정책은 무시한다.
 *
 * @param alarmId            알람 ID. 알람과 무관한 알림(플로우 알림 노드)이면 null
 * @param event              알림 사건({@link NotificationEvents}). 템플릿 선택·멱등 키에 쓴다
 * @param eventSeq           같은 알람·사건의 순번(재발생 횟수 등). 멱등 키 {@code sha256(alarmId, event, recipient, channel, eventSeq)}
 * @param severity           심각도. 사용자 최소 심각도·방해 금지 CRITICAL 예외(BR-RUL-14)에 쓴다. 없으면 null
 * @param policyId           알림 정책 ID. 정책으로 수신자를 계산해야 할 때
 * @param recipients         수신자. 정책 ID가 없으면 1명 이상
 * @param templateKeys       채널 키 → 템플릿 키. 채널 키 {@value #DEFAULT_TEMPLATE}는 모든 채널의 기본
 * @param variables          템플릿 변수(값은 JSON 원시값·객체)
 * @param aggregateWindowSec 묶기 창(0 = 끔, 60~600, BR-RUL-15). null이면 정책·채널 기본
 * @param aggregateKey       묶기 키. 같은 키·창 안의 알림을 요약 1건으로 보낸다. null이면 {@code alarmKey} 또는 {@code event}
 * @param escalation         에스컬레이션 단계(BR-RUL-16). 첫 발송이면 null
 * @param link               바로가기 링크(버튼을 못 쓰는 채널은 버튼 대신 이 링크, BR-OPS-32)
 * @param virtual            가상 기기 알람이면 true(SIM-07.04: [가상] 접두어 또는 발송 끄기)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NotificationRequest(Long alarmId, String event, Long eventSeq, AlarmSeverity severity, Long policyId,
                                  List<NotificationRecipient> recipients, Map<String, String> templateKeys,
                                  Map<String, Object> variables, Integer aggregateWindowSec, String aggregateKey,
                                  Escalation escalation, String link, Boolean virtual) {

    /** {@code templateKeys}에서 모든 채널의 기본 템플릿 키 */
    public static final String DEFAULT_TEMPLATE = "*";
    /** 묶기 창 최대(초) */
    public static final int MAX_AGGREGATE_WINDOW_SEC = 600;
    /** 묶기 창 최소(초). 0은 끔 */
    public static final int MIN_AGGREGATE_WINDOW_SEC = 60;

    public NotificationRequest {
        if (event == null || event.isBlank()) {
            throw new IllegalArgumentException("notify.event는 필수입니다");
        }
        recipients = recipients == null ? List.of() : List.copyOf(recipients);
        if (recipients.isEmpty() && policyId == null) {
            throw new IllegalArgumentException("notify.recipients가 비어 있으면 policyId가 있어야 합니다");
        }
        templateKeys = templateKeys == null ? Map.of() : Map.copyOf(templateKeys);
        variables = variables == null ? Map.of() : Map.copyOf(variables);
        if (aggregateWindowSec != null && aggregateWindowSec != 0
                && (aggregateWindowSec < MIN_AGGREGATE_WINDOW_SEC || aggregateWindowSec > MAX_AGGREGATE_WINDOW_SEC)) {
            throw new IllegalArgumentException("aggregateWindowSec는 0 또는 60~600입니다: " + aggregateWindowSec);
        }
        if (alarmId != null && alarmId < 1) {
            throw new IllegalArgumentException("alarmId는 1 이상입니다");
        }
    }

    /** 알람 알림(core-api 정책 평가 결과) */
    public static NotificationRequest forAlarm(long alarmId, String event, long eventSeq, AlarmSeverity severity,
                                               List<NotificationRecipient> recipients, Map<String, String> templateKeys,
                                               Map<String, Object> variables, Integer aggregateWindowSec, String link) {
        return new NotificationRequest(alarmId, event, eventSeq, severity, null, recipients, templateKeys, variables,
                aggregateWindowSec, null, null, link, null);
    }

    /** 에스컬레이션 단계 알림으로 바꾼다 */
    public NotificationRequest withEscalation(int stepNo) {
        return new NotificationRequest(alarmId, event, eventSeq, severity, policyId, recipients, templateKeys, variables,
                aggregateWindowSec, aggregateKey, new Escalation(stepNo), link, virtual);
    }

    /** 이 채널에 쓸 템플릿 키. 채널 전용 → 기본({@value #DEFAULT_TEMPLATE}) → null */
    public String templateKeyFor(String channel) {
        String key = templateKeys.get(channel);
        return key != null ? key : templateKeys.get(DEFAULT_TEMPLATE);
    }

    /** 수신자를 정책으로 계산해야 하는가 */
    @JsonIgnore
    public boolean needsPolicyResolution() {
        return recipients.isEmpty();
    }

    /** 에스컬레이션 단계(1~3, BR-RUL-16) */
    public record Escalation(int stepNo) {
        public static final int MAX_STEPS = 3;

        public Escalation {
            if (stepNo < 1 || stepNo > MAX_STEPS) {
                throw new IllegalArgumentException("escalation.stepNo는 1~3입니다: " + stepNo);
            }
        }
    }
}
