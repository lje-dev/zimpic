# 배포 전·후 추가 고려사항

이 문서는 기본 기능을 먼저 구현한 뒤, 외부 배포 전 보완할 내용과 배포 후 운영하면서 추가로 검토할 내용을 기록한다.

## FastAPI 이미지 업로드 보안

`02-fastapi.md`의 **8.2. 이미지 추론 라우터**는 현재 `Content-Type`과 파일 용량만 검사한다.

- 외부 배포 전 Pillow를 이용한 실제 이미지 검증, 해상도 제한, 요청 횟수 제한 등 보완 추가

## 모니터링 서비스(APM, Application Performance Monitoring)

- Spring Boot 내부 분석 → Scouter
- FastAPI 성능 지표 → Prometheus
- 서비스 간 요청 추적 → OpenTelemetry + Jaeger 또는 Tempo
- 지표 대시보드 → Grafana

### 요청 흐름

```text
React
  ↓
Spring Boot ─────────→ FastAPI ─────────→ YOLO

Spring Boot ─────────→ Scouter ─────────→ Scouter 전용 화면
FastAPI ─────────────→ Prometheus ───────→ Grafana 대시보드

Spring Boot ─┐
             ├─→ OpenTelemetry ─→ Tempo ─→ Grafana 대시보드
FastAPI ─────┘
```

### 적용 순서
1. Spring Boot에 Scouter 적용
   - Spring 내부 메서드, SQL, JVM, 외부 API 호출 분석
   -  → Scouter 전용 화면에서 확인

2. FastAPI에 Prometheus 메트릭 적용
   - 요청 수, 응답 시간, 오류율, YOLO 추론 시간 수집
   - → Grafana에서 시각화

3. Spring Boot와 FastAPI에 OpenTelemetry 적용
   - 하나의 요청이 두 서비스를 거치는 과정 추적 → Tempo에 저장
   - → Grafana에서 시각화

최종 화면
```text
Scouter 전용 화면
└── Spring Boot 상세 내부 분석

Grafana
├── Prometheus: FastAPI 성능 지표
└── Tempo: Spring Boot ↔ FastAPI 요청 추적
```

추가로 Grafana에서 Spring Boot의 기본 성능 지표까지 함께 보고 싶다면 Spring Boot Actuator와 Micrometer를 추가
```text
Spring Boot Actuator + Micrometer ─┐
FastAPI Prometheus 메트릭 ─────────┼→ Prometheus → Grafana
                                   │
OpenTelemetry → Tempo ─────────────┘
```
이렇게 하면
- 전체 상태는 Grafana
- Spring Boot 상세 분석은 Scouter라는 구성이 됩니다.