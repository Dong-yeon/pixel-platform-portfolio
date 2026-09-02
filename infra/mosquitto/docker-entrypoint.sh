#!/bin/sh
# 컨테이너가 뜰 때마다 비밀번호 파일을 새로 만든다 — 평문 비밀번호를 이미지에도
# git에도 남기지 않기 위해서다(Railway 컨테이너 파일시스템은 재배포마다 초기화되므로
# "한 번 굽고 재사용"이 애초에 안 통한다).
#
# MQTT_USERS 형식: "user1:pass1,user2:pass2,..." — Railway 서비스 환경변수로만 주입한다.
set -e

PASSWD_FILE=/mosquitto/config/passwd

# 매번 지우고 새로 만든다 — 재시작(크래시 루프 포함)마다 이전 파일이 남아 있으면
# -c(새로 만들기)가 "파일이 이미 있다"며 실패한다(실제로 겪었다: 첫 부팅은 성공했는데
# mosquitto가 권한을 낮춘 뒤 이 파일을 못 읽어 죽고, 재시작한 두 번째 시도부터는
# -c 자체가 실패해 크래시 루프에 빠졌다).
rm -f "$PASSWD_FILE"

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

# mosquitto_passwd가 이 파일을 root 소유·제한된 권한으로 만든다. 그런데 mosquitto는
# root로 뜬 뒤 보안을 위해 스스로 mosquitto 사용자로 권한을 낮추므로(config에 명시
# 안 해도 이미지 기본 동작), 소유권을 넘겨주지 않으면 낮춘 권한으로 이 파일을 못
# 열어 곧바로 죽는다("Unable to open pwfile" — 실제로 겪었다).
chown mosquitto:mosquitto "$PASSWD_FILE"
chmod 600 "$PASSWD_FILE"

exec mosquitto -c /mosquitto/config/mosquitto.conf
