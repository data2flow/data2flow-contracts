package net.java21.data2flow.contracts.message;

import java.util.UUID;

/**
 * RabbitMQ로 오가는 모든 메시지 계약의 공통 모양(design/conventions.md §3, ADR-020).
 *
 * <p>본문은 JSON이고 스키마 버전 {@code v}와 메시지 ID {@code messageId}를 반드시 가진다. 소비자는 {@code messageId}로
 * 중복을 거르고(최소 1회 전달), {@code v}로 읽을 수 있는 버전인지 확인한다. 구현 record에는 {@link MessageSchema}를 붙인다.
 */
public interface Message {

    /** 스키마 버전. 호환을 깨는 변경에만 올린다(필드 추가는 같은 버전, 소비자는 모르는 필드를 무시한다) */
    int v();

    /** 메시지 ID(UUID). 하위 단계의 중복 제거 키 */
    UUID messageId();
}
