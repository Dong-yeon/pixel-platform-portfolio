#!/bin/sh
# 컨테이너가 뜰 때마다 비밀번호 파일을 새로 만든다 — 평문 비밀번호를 이미지에도
# git에도 남기지 않기 위해서다(Railway 컨테이너 파일시스템은 재배포마다 초기화되므로
# "한 번 굽고 재사용"이 애초에 안 통한다).
#
# MQTT_USERS 형식: "user1:pass1,user2:pass2,..." — Railway 서비스 환경변수로만 주입한다.
set -e

PASSWD_FILE=/mosquitto/config/passwd

if [ -n "$MQTT_USERS" ]; then
    first=1
    OLDIFS=$IFS
    IFS=','
    for pair in $MQTT_USERS; do
        user=${pair%%:*}
        pass=${pair#*:}
        if [ "$first" = "1" ]; then
            mosquitto_passwd -c -b "$PASSWD_FILE" "$user" "$pass"
            first=0
        else
            mosquitto_passwd -b "$PASSWD_FILE" "$user" "$pass"
        fi
    done
    IFS=$OLDIFS
else
    # MQTT_USERS가 비어 있으면 아무도 인증을 못 통과한다 — 익명으로 조용히 열리는 것보다
    # 이쪽이 안전한 실패 방향이다(fail closed).
    echo "WARNING: MQTT_USERS is not set. No accounts will be provisioned; all connections will be rejected." >&2
    : > "$PASSWD_FILE"
fi

exec mosquitto -c /mosquitto/config/mosquitto.conf
