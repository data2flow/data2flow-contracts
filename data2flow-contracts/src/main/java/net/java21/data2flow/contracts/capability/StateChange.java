package net.java21.data2flow.contracts.capability;

/**
 * 상태 속성 하나의 변화(EVT-ACT-02 {@code changed[]}).
 *
 * @param capability 기능
 * @param attribute  속성
 * @param from       이전 값. 처음 보고면 null
 * @param to         새 값. 사라졌으면 null
 */
public record StateChange(String capability, String attribute, Object from, Object to) {
}
