# data2flow-contracts

data2flow 서비스들이 함께 쓰는 계약 라이브러리입니다. 모듈은 두 개입니다.

| 모듈 | 내용 |
|---|---|
| `data2flow-bom` | 공통 의존성 버전 목록. 서비스는 이 BOM을 import하고 버전 없이 이름만 씁니다 |
| `data2flow-contracts` | 공통 응답(`ApiResponse`·`ListApiResponse`·`CursorListApiResponse`), 오류 코드와 예외 처리(`GlobalExceptionHandler`), 4개 언어(ko·en·ja·zh) 오류 문구, 신원·요청 헤더(`X-USER-ID`, `X-ORG-ID`, `X-REQUEST-ID`, `X-CALLER-SERVICE`, `Idempotency-Key`), RabbitMQ 이름 상수 |

규칙의 정본은 `data2flow-docs`의 `design/api-rules.md`(ADR-035)와 `design/conventions.md` §3입니다.

## 빌드

```bash
./mvnw verify      # 테스트 + 커버리지 80% 검사
./mvnw install     # 로컬 저장소에 설치(다른 서비스 로컬 빌드용)
```

`main`에 병합되면 CI가 GitHub Packages에 배포합니다. 서블릿 서비스는 의존성만 추가하면 예외 처리·Accept-Language·요청 ID 필터가 자동으로 켜집니다. 리액티브 gateway에서는 상수와 오류 코드만 씁니다.
