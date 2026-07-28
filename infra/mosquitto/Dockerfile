# Mosquitto — 모듈 공용 MQTT 브로커 (Railway 프라이빗 서비스)
# 빌드 컨텍스트: infra/mosquitto
#
# Railway 프라이빗 네트워크 안에서만 접근한다(퍼블릭 도메인을 열지 않는다).
# 다른 서비스는 tcp://mosquitto.railway.internal:1883 으로 붙는다.
# 로컬용 mosquitto.conf가 아니라 IPv6까지 바인딩하는 railway 설정을 쓴다.
FROM eclipse-mosquitto:2
COPY mosquitto.railway.conf /mosquitto/config/mosquitto.conf
EXPOSE 1883
