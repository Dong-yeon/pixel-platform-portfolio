package com.pixelfleet.task.event;

/**
 * 운송 작업 생명주기 변화(완료·실패). <b>모듈 밖으로 나가는 통지의 씨앗</b>이다.
 *
 * <p>fleet은 누가 이걸 듣는지 모른다 — 애플리케이션 이벤트로 던지면 MQTT 어댑터가 커밋 후
 * 토픽에 발행하고, 관심 있는 모듈(WMS 등)이 구독한다. fleet이 WMS를 알게 되는 순간
 * 컴포저블이 깨지므로 <b>수신자를 지목하지 않는다</b>.
 *
 * @param taskCode 작업 코드 — 다른 모듈이 자기 전표를 되찾는 열쇠(fleet 내부 id가 아니다)
 * @param event    {@code completed} | {@code failed}
 * @param reason   실패 사유(완료면 null)
 */
public record TaskLifecycleChanged(String taskCode, String event, String reason) {

    public static TaskLifecycleChanged completed(String taskCode) {
        return new TaskLifecycleChanged(taskCode, "completed", null);
    }

    public static TaskLifecycleChanged failed(String taskCode, String reason) {
        return new TaskLifecycleChanged(taskCode, "failed", reason);
    }
}
