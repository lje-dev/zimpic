# Spring Boot 백엔드 준비 및 단계별 구현

이 문서는 기존 Django 백엔드의 일반 API, 데이터베이스 처리와 견적 계산 기능을 Spring Boot로 옮기는 과정을 설명한다.

기존 `backend/`는 이전이 끝날 때까지 수정하거나 삭제하지 않고, 같은 입력에 대한 응답과 계산 결과를 비교하는 용도로 유지한다. FastAPI는 YOLO 추론만 담당하며 PostgreSQL에는 접근하지 않는다.

```text
React
  ↓
Spring Boot
  ├── PostgreSQL
  ├── FastAPI + YOLO
  └── Kakao API
```

## 1. 기준 기술

| 구분         | 선택                        |
| ------------ | --------------------------- |
| Java         | Java 21                     |
| Spring Boot  | 4.1.1                       |
| 빌드 도구    | Gradle Groovy               |
| 데이터베이스 | PostgreSQL 17               |
| ORM          | Spring Data JPA와 Hibernate |
| 웹           | Spring MVC                  |
| FastAPI 호출 | Spring `RestClient`         |
| 테스트       | JUnit 5와 Spring Boot Test  |

Spring Initializr에서 선택할 수 있는 정식 안정 버전인 Spring Boot 4.1.1과 Java 21을 사용한다. `SNAPSHOT`은 개발 중인 빌드이고 `M`이 붙은 버전은 마일스톤 시험 버전이므로 선택하지 않는다.

Spring Boot 4.1.1은 Java 17 이상을 요구하며 Java 21을 지원한다. Gradle은 8.14 이상 또는 9.x를 지원하지만, 직접 설치하지 않고 프로젝트와 함께 생성되는 호환 Gradle Wrapper를 사용한다. 자세한 기준은 [Spring Boot 시스템 요구사항](https://docs.spring.io/spring-boot/system-requirements.html)을 참고한다.

Gradle은 시스템에 별도로 설치하지 않고 Spring Initializr가 생성하는 Gradle Wrapper를 사용한다.

```bash
./gradlew
```

Windows에서는 다음 명령을 사용한다.

```powershell
.\gradlew.bat
```

## 2. 사전 준비 확인

### Java

```bash
java -version
javac -version
```

두 명령 모두 Java 21을 표시해야 한다.

### PostgreSQL

macOS에서 Homebrew로 설치했다면 다음 명령으로 상태를 확인한다.

```bash
brew services list | grep postgresql
pg_isready -h 127.0.0.1 -p 5432
```

정상 결과는 다음과 같다.

```text
127.0.0.1:5432 - accepting connections
```

애플리케이션 계정으로 실제 접속도 확인한다.

```bash
psql -h 127.0.0.1 -p 5432 -U zimpic_user -d zimpic
```

접속 후 현재 데이터베이스와 사용자를 확인한다.

```sql
SELECT current_database(), current_user;
```

아직 Spring Boot를 실행하지 않았으므로 `\dt` 명령에 테이블이 표시되지 않아도 정상이다.

### FastAPI

`yolo/`에서 가상환경을 활성화하고 FastAPI를 실행한다.

```bash
source .venv/bin/activate
fastapi dev app/main.py
```

다른 터미널에서 상태를 확인한다.

```bash
curl http://127.0.0.1:8000/health
```

예상 응답은 다음과 같다.

```json
{ "status": "ok" }
```

## 3. Spring Boot 프로젝트 생성

[Spring Initializr](https://start.spring.io/)에서 다음 항목을 선택한다.

| 항목         | 값                   |
| ------------ | -------------------- |
| Project      | Gradle - Groovy      |
| Language     | Java                 |
| Spring Boot  | 4.1.1                |
| Group        | `com.zimpic`         |
| Artifact     | `spring-backend`     |
| Name         | `spring-backend`     |
| Package name | `com.zimpic.backend` |
| Packaging    | Jar                  |
| Java         | 21                   |

다음 의존성을 추가한다.

| 의존성                     | 역할                                                         |
| -------------------------- | ------------------------------------------------------------ |
| Spring Web                 | Spring MVC 기반 Controller와 REST API 구현, 내장 Tomcat 사용 |
| Spring Data JPA            | Entity와 Repository 구현                                     |
| PostgreSQL Driver          | PostgreSQL JDBC 연결                                         |
| Validation                 | API 요청값 검증                                              |
| Spring Boot Actuator       | 애플리케이션과 DB 상태 확인                                  |
| HTTP Client (`RestClient`) | FastAPI와 Kakao API를 호출하는 동기식 HTTP 클라이언트        |
| OpenAPI·Swagger UI         | Controller와 DTO를 분석해 API 문서를 생성하고 브라우저에서 확인 |
| Lombok                     | 반복되는 Getter와 생성자 코드 간소화                         |

처음에는 Spring Security, WebFlux와 Flyway를 추가하지 않는다.

- 현재 이전 대상에는 인증 기능이 없다.
- Initializr 화면에서는 `Spring Web`을 선택한다. 이 의존성으로 Spring MVC 기반 REST API와 내장 Tomcat을 사용한다.
- 일반적인 동기식 API와 YOLO 응답 대기에는 Spring MVC와 `RestClient`를 사용한다.
- 개발 중에는 로드맵대로 `ddl-auto=update`를 사용하고 전체 로컬 테스트가 끝난 뒤 Flyway를 도입한다.

Lombok은 다음 범위에서 사용한다.

- JPA Entity: `@Getter`, `@NoArgsConstructor(access = AccessLevel.PROTECTED)` 사용
- 의존성 주입이 필요한 클래스: `@RequiredArgsConstructor` 사용
- 값 변경은 필요한 메서드로 명시하고 Entity에 `@Setter`를 일괄 적용하지 않음
- Entity에는 `@Data`를 사용하지 않음
- API 요청과 응답 DTO는 Java `record`를 우선 사용

IntelliJ IDEA에서 Lombok 코드가 인식되지 않으면 Lombok 플러그인과 Annotation Processing 설정을 확인한다.

생성한 프로젝트의 압축을 풀어 저장소 루트의 `spring-backend/`에 둔다.

```text
zimpic/
├── backend/
├── frontend/
├── spring-backend/
├── yolo/
└── docs/
```

## 4. 최초 실행

기능을 추가하기 전에 생성된 프로젝트가 정상적으로 컴파일되는지 확인한다. Spring Data JPA를 추가한 프로젝트의 `contextLoads()` 테스트와 애플리케이션 실행에는 DB 접속 설정이 필요하므로 이 단계에서는 실행하지 않는다.

```bash
cd spring-backend
./gradlew classes # 컴파일
```

완료 기준은 다음과 같다.

- Gradle 의존성 다운로드가 성공한다.
- Java 소스 컴파일이 성공한다.

`./gradlew test`와 `./gradlew bootRun`은 다음 단계에서 PostgreSQL 접속 정보를 설정한 뒤 실행한다. 기본 서버 포트는 `8080`이다.

## 5. 환경설정

Initializr가 생성한 `src/main/resources/application.properties`에서 공통 설정을 관리한다. 파일 이름을 변경하지 않고 기존 `spring.application.name` 아래에 설정을 추가한다. DB 비밀번호와 외부 API 키는 환경변수로 전달한다.

```properties
# 애플리케이션 이름
spring.application.name=spring-backend

# PostgreSQL JDBC 주소
# DB_URL이 없으면 로컬 PostgreSQL 주소를 기본값으로 사용한다.
# Docker Compose에서는 DB_URL을 jdbc:postgresql://postgres:5432/zimpic으로 전달한다.
spring.datasource.url=${DB_URL:jdbc:postgresql://localhost:5432/zimpic}

# Spring Boot가 PostgreSQL에 접속할 때 사용할 애플리케이션 전용 사용자
spring.datasource.username=${DB_USERNAME:zimpic_user}

# DB 비밀번호는 설정 파일에 직접 작성하지 않고 환경변수로만 전달한다.
spring.datasource.password=${DB_PASSWORD}

# 개발 중 Entity 변경사항을 테이블에 반영한다. 운영 환경에서는 사용하지 않는다.
spring.jpa.hibernate.ddl-auto=update

# Controller 응답을 만드는 동안 지연 로딩이 발생하지 않도록 영속성 컨텍스트 범위를 Service 계층까지로 제한한다.
spring.jpa.open-in-view=false

# FastAPI 기본 주소
# YOLO_BASE_URL이 없으면 로컬 FastAPI 주소를 사용하고, Docker에서는 서비스 이름으로 변경한다.
yolo.base-url=${YOLO_BASE_URL:http://localhost:8000}

# Actuator의 기본 /actuator 경로를 /로 변경하고 health만 외부에 노출한다.
# 기본 주소인 /actuator/health 대신 로드맵에서 정한 /health를 사용한다.
management.endpoints.web.base-path=/
management.endpoints.web.path-mapping.health=health
management.endpoints.web.exposure.include=health

# health 응답에 DB 주소와 같은 상세 내부 정보를 노출하지 않는다.
management.endpoint.health.show-details=never
```

실행 전에 로컬 DB 비밀번호를 환경변수로 설정한다.

```bash
export DB_PASSWORD="로컬에서_설정한_비밀번호"
./gradlew bootRun
```

Windows PowerShell에서는 다음과 같이 설정한다.

```powershell
$env:DB_PASSWORD="로컬에서_설정한_비밀번호"
.\gradlew.bat bootRun
```

설정을 마치고 테스트와 애플리케이션 실행을 확인한다.

```bash
./gradlew test
./gradlew bootRun
```

`DB_PASSWORD`와 `KAKAO_REST_API_KEY`는 Git에 올라가는 설정 파일에 작성하지 않는다. `.env` 파일은 Spring Boot가 기본적으로 자동으로 읽지 않으므로 파일을 만드는 것만으로는 설정이 적용되지 않는다.

처음에는 다음 환경만 구분한다.

- `local`: 로컬 PostgreSQL과 FastAPI 사용
- `test`: 테스트용 설정

Docker와 운영 환경 설정은 로컬 기능 검증이 끝난 뒤 추가한다.

## 6. Health API

### Health API가 필요한 이유

Health API는 서비스가 현재 요청을 처리할 수 있는 상태인지 확인하는 API다. 브라우저 화면을 제공하는 기능이 아니라 실행 상태를 JSON으로 알려준다. 개발자가 직접 확인할 때뿐 아니라 이후 Docker, Nginx와 AWS도 이 주소를 이용해 Spring Boot의 상태를 확인할 수 있다.

### 직접 Controller를 만들지 않았는데 응답하는 이유

프로젝트를 생성할 때 `Spring Boot Actuator` 의존성을 추가했다. Spring Boot는 실행 중 Actuator 의존성을 발견하면 상태 확인 기능과 URL을 자동으로 등록한다. 따라서 아직 직접 작성한 Controller가 없어도 기본 주소에 요청하면 응답을 받을 수 있다.

```text
브라우저 또는 curl
  → GET /actuator/health
  → Spring MVC가 Actuator의 Health Endpoint로 전달
  → 애플리케이션과 연결된 구성 요소의 상태 확인
  → JSON 응답 반환
```

설정을 변경하기 전 Actuator의 기본 주소는 다음과 같다.

```text
GET /actuator/health
```

브라우저 주소창에 URL을 입력하거나 `curl`을 실행하면 해당 주소로 HTTP `GET` 요청이 전송된다.

```bash
curl http://127.0.0.1:8080/actuator/health
```

정상 응답 예시는 다음과 같다.

```json
{
  "groups": ["liveness", "readiness"],
  "status": "UP"
}
```

- `status: UP`: 현재 등록된 상태 검사 항목이 정상이라는 의미
- `liveness`: Spring Boot 프로세스가 살아 있는지 확인하는 상태 그룹
- `readiness`: 외부 요청을 받을 준비가 되었는지 확인하는 상태 그룹

Spring Data JPA로 DataSource가 만들어지면 Actuator는 PostgreSQL 연결 상태도 health 판단에 포함한다. `show-details=never`로 설정했기 때문에 외부 응답에는 DB 주소나 개별 검사 결과를 표시하지 않고 전체 상태만 반환한다.

### `/actuator/health`를 `/health`로 변경

기본 주소를 그대로 사용해도 기능상 문제는 없다. 이 프로젝트에서는 FastAPI의 `/health`와 주소 형식을 맞추고 로드맵의 약속을 유지하기 위해 Spring Boot 주소도 `/health`로 변경한다.

앞의 환경설정에 추가한 다음 속성이 Actuator의 기본 경로를 변경한다.

```properties
# Actuator Endpoint 앞에 붙는 기본 /actuator 경로를 /로 변경
management.endpoints.web.base-path=/

# health Endpoint의 경로 이름을 health로 지정
management.endpoints.web.path-mapping.health=health

# 여러 Actuator 기능 중 health만 HTTP로 노출
management.endpoints.web.exposure.include=health

# DB 주소와 같은 상세 내부 정보는 응답에 표시하지 않음
management.endpoint.health.show-details=never
```

설정을 변경한 뒤에는 실행 중인 Spring Boot를 `Ctrl + C`로 종료하고 다시 실행해야 한다.

```bash
./gradlew bootRun
```

재실행한 뒤에는 다음 주소를 사용한다.

```text
GET /health
```

정상 응답 예시는 다음과 같다.

```json
{ "status": "UP" }
```

다음 명령으로 확인한다.

```bash
curl http://127.0.0.1:8080/health
```

루트 주소인 `http://127.0.0.1:8080/`에 Whitelabel Error Page가 표시되는 것은 `/`를 처리할 Controller가 아직 없기 때문이다. `/health` 응답과는 별개의 동작이며 서버 실행 실패를 뜻하지 않는다.

PostgreSQL을 중지하면 DB 상태 확인이 실패하므로 전체 health 상태도 `DOWN`으로 바뀌고 일반적으로 HTTP `503 Service Unavailable`이 반환된다. PostgreSQL을 다시 시작하면 `UP`으로 돌아와야 한다.

완료 기준은 다음과 같다.

- Spring Boot가 정상적으로 실행된다.
- `/health`가 정상 응답을 반환한다.
- PostgreSQL 연결 성공 여부가 health 상태에 반영된다.

## 7. OpenAPI와 Swagger UI 확인

Django와 FastAPI에서 사용하던 것처럼 Spring Boot에서도 실행 중인 API 목록과 요청·응답 형식을 브라우저에서 확인할 수 있다.

Spring Boot에는 Swagger UI가 기본으로 포함되어 있지 않다. 이 프로젝트는 생성 단계에서 추가한 `springdoc-openapi`를 사용한다. `springdoc-openapi`는 Spring MVC의 Controller, 요청 경로, 요청 DTO와 응답 DTO를 분석해 OpenAPI 문서를 자동으로 생성한다.

앞 단계에서 `/health`를 확인한 Spring Boot를 계속 실행한 상태로 다음 주소를 확인한다.

| 용도 | 주소 |
| --- | --- |
| Swagger UI | `http://127.0.0.1:8080/swagger-ui.html` |
| OpenAPI JSON | `http://127.0.0.1:8080/v3/api-docs` |
| OpenAPI YAML | `http://127.0.0.1:8080/v3/api-docs.yaml` |

브라우저에서 Swagger UI 주소를 열면 `/swagger-ui/index.html`로 이동할 수 있다. 화면이 표시되면 Swagger UI가 정상적으로 등록된 것이다.

OpenAPI JSON은 브라우저에서 열거나 다음 명령으로 확인한다.

```bash
curl http://127.0.0.1:8080/v3/api-docs
```

현재는 직접 작성한 Controller가 없으므로 Swagger UI에 표시되는 API가 없거나 적은 것이 정상이다. 이후 `@GetMapping`, `@PostMapping` 등이 선언된 Controller를 만들면 해당 API가 자동으로 추가된다. Actuator의 `/health`는 일반 Controller가 아니므로 기본 Swagger 문서에 표시되지 않을 수 있다.

완료 기준은 다음과 같다.

- `/swagger-ui.html`에 접속할 수 있다.
- `/v3/api-docs`가 OpenAPI JSON을 반환한다.
- 현재 구현 단계에서는 문서화된 자체 API가 없을 수 있음을 확인한다.

## 8. 패키지 구조

이 프로젝트에서는 Controller, Service, Repository처럼 역할을 먼저 구분하는 계층형 패키지 구조를 사용한다. Spring Boot를 처음 학습할 때 요청이 각 계층을 통과하는 흐름을 확인하기 쉽고, 현재 프로젝트 규모에서도 충분히 관리할 수 있다.

```text
com.zimpic.backend
├── config                         # Actuator, HTTP Client, CORS 등 애플리케이션 설정
├── controller                     # HTTP 요청을 받고 응답 반환
├── dto
│   ├── request                    # React와 외부에서 받는 요청 형식
│   └── response                   # React와 외부에 반환하는 응답 형식
│       ├── ApiResponse.java       # record: 정상 응답의 공통 JSON 형식
│       └── ErrorResponse.java     # record: 오류 응답의 공통 JSON 형식
├── entity                         # PostgreSQL 테이블과 연결되는 JPA 객체
│   ├── Furniture.java             # class: JPA Entity
│   ├── FurnitureRotation.java     # class: JPA Entity
│   ├── TruckSpec.java             # class: JPA Entity
│   ├── Estimate.java              # class: JPA Entity
│   ├── EstimateItem.java          # class: JPA Entity
│   ├── type                       # Entity 필드에 사용하는 Enum과 값 타입
│   │   ├── FurnitureCategory.java # enum: 일반가전, 주방가전, 일반가구, 특수가구, 기타 분류
│   │   ├── RotationCode.java      # enum: 가구의 가로·세로·높이 회전 방향 조합
│   │   ├── TruckType.java         # enum: 1톤, 2.5톤, 5톤 등 트럭 적재 용량
│   │   ├── BodyType.java          # enum: 카고, 탑차 등 트럭 차체 종류
│   │   ├── MoveType.java          # enum: 일반이사, 포장이사 등 이사 종류
│   │   ├── PriceType.java         # enum: 기본, 사다리, 계단, 거리, 특수 품목 비용 구분
│   │   └── PriceLocation.java     # enum: 비용이 발생한 출발지 또는 도착지
│   └── converter                  # Enum 값과 DB 문자열을 변환하는 JPA Converter
│       └── TruckTypeConverter.java # class: JPA AttributeConverter 구현
├── repository                     # 데이터 저장과 조회
├── service                        # 비즈니스 로직과 트랜잭션
├── client                         # FastAPI와 Kakao API 호출
└── exception                      # 공통 예외와 API 오류 응답 처리
    ├── ErrorCode.java             # enum: 오류별 HTTP 상태, 코드와 기본 메시지
    ├── BusinessException.java     # class: RuntimeException을 상속하는 업무 예외
    └── GlobalExceptionHandler.java # class: 발생한 예외를 공통 오류 응답으로 변환
```

트리에서 디렉터리는 Java `package`로 만들고, `.java` 파일은 주석에 표시한 `class`, `record`, `enum` 타입으로 만든다. `repository`에 추가할 파일은 Spring Data JPA가 구현하도록 `interface`로 작성하며, 요청 DTO도 값 전달만 담당하면 `record`를 우선 사용한다.

기존 Django 구조와 Spring Boot 패키지는 완전한 1:1 관계가 아니며, 기존 파일의 역할에 따라 다음과 같이 나누어 옮긴다.

| 기존 Django | Spring Boot | 역할 |
| --- | --- | --- |
| `models.py` | `entity/`, `entity/type/`, `repository/` | DB 모델, Enum과 데이터 조회 |
| `serializers.py` | `dto/request/`, `dto/response/` | 요청·응답 형식과 입력값 검증 |
| `views.py` | `controller/` | HTTP 요청 수신과 응답 반환 |
| `urls.py` | Controller의 `@RequestMapping` | API 주소 연결 |
| `services/` | `service/`, `client/` | 계산 로직과 외부 API 호출 |
| `management/commands/` | `db/seed/*.sql` | 개발용 정책 초기 데이터 수동 적재 |
| `migrations/` | 개발 단계의 JPA, 로컬 테스트 후 Flyway | 테이블 생성과 변경 관리 |
| `admin.py` | 직접 대응 없음 | 관리자 기능은 Django 제거 전 별도 결정 |

기본 호출 방향은 다음과 같다.

```text
Controller → Service → Repository → PostgreSQL
                └──→ Client → FastAPI 또는 Kakao API
```

Controller가 Repository를 직접 호출하거나 Repository가 Service를 호출하지 않는다. 패키지에 클래스가 많아지면 각 계층 아래에 `policy`, `vision`, `estimate` 하위 패키지를 추가한다.

`FurnitureCategory`, `MoveType`처럼 Enum 이름과 DB 문자열이 같은 값은 `@Enumerated(EnumType.STRING)`으로 저장한다. `TruckType`은 Java Enum 상수와 기존 DB·CSV 값인 `1T`, `2_5T`가 다르므로 `TruckTypeConverter`를 사용한다.

JPA Entity를 Controller 응답으로 직접 반환하지 않고 기존 React API 형식에 맞는 DTO를 별도로 작성한다.

## 9. 공통 API 응답과 오류 처리

Spring Boot API는 성공과 실패 응답을 일관된 JSON 형식으로 반환한다. HTTP 상태 코드는 요청 처리 결과에 맞게 사용하고, JSON 안에 `status`를 중복해서 넣지 않는다.

관련 클래스는 다음 위치에 둔다.

```text
dto/response/
├── ApiResponse.java
└── ErrorResponse.java

exception/
├── ErrorCode.java
├── BusinessException.java
└── GlobalExceptionHandler.java
```

- `ApiResponse<T>`: 정상 응답 데이터를 `success`와 `data` 형식으로 반환
- `ErrorResponse`: 오류를 `success`, `code`, `message` 형식으로 반환
- `ErrorCode`: 오류별 HTTP 상태, 코드와 기본 메시지를 한곳에서 관리
- `BusinessException`: Service에서 발생한 업무 규칙 오류와 필요한 사용자 메시지를 전달
- `GlobalExceptionHandler`: 예외 종류에 맞는 HTTP 상태와 `ErrorResponse`를 공통 생성

정상 응답과 오류 응답 DTO는 Java `record`로 작성한다.

`record`는 API 응답처럼 값을 전달하기 위한 객체를 간단하게 선언하는 Java 문법이다. `record` 이름 뒤 괄호 안에 타입과 이름을 선언하면, 괄호에 선언한 모든 값을 받는 생성자와 값 조회 메서드 등을 Java가 자동으로 만든다. 인자가 없는 기본 생성자를 만드는 것이 아니라 선언된 값을 모두 받는 생성자를 만드는 것이다.

예를 들어 다음 `record`는 `message` 값을 받는 생성자와 `message()` 조회 메서드를 자동으로 제공한다.

```java
public record ExampleResponse(String message) {
}
```

위 선언에서 `String message`는 내부에 저장할 값, 생성자의 매개변수, `message()` 조회 메서드를 한 번에 정의한다. 따라서 생성자와 조회 메서드를 직접 작성하지 않아도 다음처럼 객체를 생성하고 값을 읽을 수 있다.

```java
ExampleResponse response = new ExampleResponse("요청에 성공했습니다.");
String message = response.message();
```

일반 클래스의 getter인 `getMessage()`가 아니라 선언한 이름과 같은 `message()`로 값을 읽는다. `record`로 만든 객체에는 값을 변경하는 setter가 없으므로 응답 DTO처럼 생성한 값을 그대로 전달하는 용도에 적합하다.

`<T>`는 실제 타입을 사용하는 시점에 결정하는 제네릭 타입이다. `ApiResponse<T>`에서 `T`는 `data`에 들어갈 타입의 자리이며, 응답마다 다른 데이터를 담아도 `ApiResponse` 하나를 공통으로 사용할 수 있게 한다.

```java
ApiResponse<String>
ApiResponse<FurnitureResponse>
ApiResponse<List<FurnitureResponse>>
```

각 선언에서 `T`는 각각 `String`, `FurnitureResponse`, `List<FurnitureResponse>`가 된다.

```java
public record ApiResponse<T>(
    boolean success,
    T data
) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data);
    }
}
```

`success()`는 제네릭을 사용하는 메서드의 표준 문법으로 작성한다.

```text
접근제어자 static <제네릭 타입> 반환 타입       메서드명(매개변수)
public     static <T>           ApiResponse<T> success(T data)
                   ↑                 ↑                  ↑
              타입 T 선언      반환할 객체 타입     받는 데이터 타입
```

`static` 메서드는 `ApiResponse<T>` 객체에 소속되지 않으므로 record에 선언된 `T`를 그대로 사용할 수 없다. 따라서 `<T>`로 이 메서드가 사용할 타입을 다시 선언하고, `T data`로 값을 받은 다음 `ApiResponse<T>`에 담아 반환한다. 실제 타입은 전달한 객체를 보고 Java가 결정한다.

예를 들어 `FurnitureResponse` 객체를 전달하면 `T`는 `FurnitureResponse`가 된다.

```java
FurnitureResponse furniture = ...;

ApiResponse<FurnitureResponse> response =
    ApiResponse.success(furniture);
```

이때 메서드는 개념적으로 다음 형태가 되며, 생성되는 응답은 `success=true`이고 `data`에는 `furniture` 객체가 들어간다.

```java
public static ApiResponse<FurnitureResponse> success(
    FurnitureResponse data
)
```

`success()`는 `static` 정적 팩토리 메서드이므로 `ApiResponse` 객체를 먼저 만들지 않고 `ApiResponse.success(data)` 형태로 호출한다.

### 정상 응답

정상 응답 예시는 다음과 같다.

```http
HTTP/1.1 200 OK
```

```json
{
  "success": true,
  "data": {
    "id": 1,
    "nameKr": "침대"
  }
}
```

탐지 결과가 없는 것처럼 정상적으로 처리됐지만 결과 목록이 빈 경우는 오류로 만들지 않는다.

```json
{
  "success": true,
  "data": {
    "detections": []
  }
}
```

### 실패 응답

오류 응답 예시는 다음과 같다.

```http
HTTP/1.1 404 Not Found
```

```json
{
  "success": false,
  "code": "FURNITURE_NOT_FOUND",
  "message": "해당 가구 정보를 찾을 수 없습니다."
}
```

```java
package com.zimpic.backend.dto.response;

import com.zimpic.backend.exception.ErrorCode;

public record ErrorResponse(
    boolean success,
    String code,
    String message
) {
    public static ErrorResponse of(ErrorCode errorCode, String message) {
        return new ErrorResponse(false, errorCode.name(), message);
    }
}
```

`ErrorResponse`와 `ErrorCode`는 서로 다른 패키지에 있으므로 `ErrorCode`를 import해야 한다. `of()`는 Java 예약어가 아니라 오류 코드와 메시지로 실패 응답 객체를 만드는 정적 팩토리 메서드다. `errorCode.name()`은 enum 상수 이름을 `FURNITURE_NOT_FOUND`와 같은 문자열로 반환한다.

### ErrorCode

`ErrorCode`는 오류마다 사용할 HTTP 상태와 기본 메시지를 한곳에서 관리한다. 처음에는 현재 단계와 이후 외부 API 연동에서 공통으로 사용할 최소 항목만 정의하고, 새로운 업무 오류가 생길 때 구체적인 코드를 추가한다.

```java
package com.zimpic.backend.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    // 요청과 데이터 조회 오류
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청값을 확인해 주세요."),
    FURNITURE_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 가구 정보를 찾을 수 없습니다."),
    BUSINESS_RULE_VIOLATION(HttpStatus.UNPROCESSABLE_CONTENT, "처리할 수 없는 요청입니다."),

    // FastAPI와 Kakao API 같은 외부 서비스 호출 오류
    EXTERNAL_API_BAD_RESPONSE(HttpStatus.BAD_GATEWAY, "외부 서비스 응답을 처리할 수 없습니다."),
    EXTERNAL_API_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "외부 서비스 응답 시간이 초과되었습니다."),

    // 예상하지 못한 서버 내부 오류
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다.");

    // 클라이언트에 반환할 HTTP 상태와 기본 메시지
    private final HttpStatus httpStatus;
    private final String defaultMessage;

    // 각 enum 상수의 상태와 메시지를 저장한다.
    ErrorCode(HttpStatus httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    // GlobalExceptionHandler에서 HTTP 응답 상태를 만들 때 사용한다.
    public HttpStatus httpStatus() {
        return httpStatus;
    }

    // 별도 메시지가 없을 때 사용자 응답에 사용할 메시지를 반환한다.
    public String defaultMessage() {
        return defaultMessage;
    }
}
```

enum 상수 뒤의 괄호는 각 상수를 만들 때 생성자에 전달하는 값이다. 예를 들어 `FURNITURE_NOT_FOUND`는 `HttpStatus.NOT_FOUND`와 기본 메시지를 보관한다. enum 생성자는 외부에서 직접 호출하지 않으므로 접근제어자를 작성하지 않는다.

### BusinessException

`BusinessException`은 요청 형식은 맞지만 가구를 찾지 못했거나 업무 규칙을 만족하지 못하는 것처럼 애플리케이션이 의도적으로 사용자에게 알려야 하는 오류를 나타낸다. 예외 처리 흐름에 참여할 수 있도록 `RuntimeException`을 상속한다.

```java
package com.zimpic.backend.exception;

public class BusinessException extends RuntimeException {
    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.defaultMessage());
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
```

첫 번째 생성자는 `ErrorCode`의 기본 메시지를 사용한다. `this(...)`는 같은 클래스의 다른 생성자를 호출하는 문법이다. 두 번째 생성자는 상황에 맞는 메시지를 직접 전달할 때 사용하며, `super(message)`로 부모인 `RuntimeException`에 메시지를 저장한다.

다음 코드는 `ErrorResponse.java`나 `BusinessException.java` 안에 작성하는 코드가 아니라, 이후 Service에서 예외를 발생시킬 때의 사용 예시다. 별도 메시지를 전달하지 않으면 `ErrorCode`의 기본 메시지를 사용한다.

```java
throw new BusinessException(ErrorCode.FURNITURE_NOT_FOUND);
```

```java
throw new BusinessException(
    ErrorCode.FURNITURE_NOT_FOUND,
    "가구 ID " + furnitureId + "에 해당하는 정보를 찾을 수 없습니다."
);
```

`throw`가 실행되면 현재 메서드의 정상 처리가 중단되고 발생한 예외가 호출한 쪽으로 전달된다.

### GlobalExceptionHandler

`GlobalExceptionHandler`는 Controller 처리 중 발생한 예외를 한곳에서 받아 HTTP 상태와 `ErrorResponse`로 변환한다. `@RestControllerAdvice`를 사용하면 모든 REST Controller에 공통으로 적용된다.

```java
package com.zimpic.backend.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.zimpic.backend.dto.response.ErrorResponse;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(
        BusinessException exception
    ) {
        ErrorCode errorCode = exception.errorCode();
        ErrorResponse response = ErrorResponse.of(errorCode, exception.getMessage());

        return ResponseEntity
            .status(errorCode.httpStatus())
            .body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(
        Exception exception
    ) {
        log.error("Unexpected exception", exception);

        ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERROR;
        ErrorResponse response = ErrorResponse.of(
            errorCode,
            errorCode.defaultMessage()
        );

        return ResponseEntity
            .status(errorCode.httpStatus())
            .body(response);
    }
}
```

`BusinessException`은 정해진 오류 코드와 사용자 메시지를 그대로 응답에 사용한다. 그 밖의 예상하지 못한 예외는 전체 내용을 서버 로그에 기록하되, 응답에는 예외 클래스명이나 원문을 넣지 않고 공통 `500` 메시지만 반환한다. 요청 DTO의 Validation 오류를 `400`으로 변환하는 처리는 실제 요청 DTO를 구현할 때 추가한다.

기본 HTTP 상태 기준은 다음과 같다.

| 상태 | 사용 상황 |
| --- | --- |
| `200 OK` | 정상 조회와 계산 |
| `201 Created` | 견적 등 데이터 생성 성공 |
| `400 Bad Request` | 요청 형식 또는 입력값 검증 실패 |
| `404 Not Found` | 가구나 견적 데이터가 없음 |
| `409 Conflict` | 중복 데이터 등 현재 상태와 요청이 충돌 |
| `422 Unprocessable Content` | 요청 형식은 맞지만 업무 규칙상 처리할 수 없음 |
| `502 Bad Gateway` | FastAPI 또는 Kakao API가 잘못된 응답을 반환 |
| `504 Gateway Timeout` | FastAPI 또는 Kakao API 응답 시간 초과 |
| `500 Internal Server Error` | 예상하지 못한 내부 오류 |

예상하지 못한 내부 예외의 클래스명, DB 오류와 외부 API 원문은 로그에만 기록하고 사용자 응답에는 노출하지 않는다.

공통 응답 형식은 기존 Django API 응답을 감싸는 새로운 계약이다. Django와 계산 결과를 비교할 때는 `data` 내부 값을 비교하고, React를 Spring Boot에 연결하는 단계에서 `response.data`를 읽도록 API 서비스 코드를 함께 변경한다.

### 공통 응답과 오류 처리 최소 테스트

아직 실제 Controller와 Service가 없으므로 Spring 전체 실행 환경을 불러오지 않는 단위 테스트로 각 객체와 Handler의 기본 동작을 확인한다. 다음 파일을 만든다.

```text
spring-backend/src/test/java/com/zimpic/backend/exception/CommonResponseTest.java
```

```java
package com.zimpic.backend.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.zimpic.backend.dto.response.ApiResponse;
import com.zimpic.backend.dto.response.ErrorResponse;

class CommonResponseTest {

    @Test
    void successResponseContainsData() {
        ApiResponse<String> response = ApiResponse.success("data");

        assertTrue(response.success());
        assertEquals("data", response.data());
    }

    @Test
    void errorResponseContainsCodeAndMessage() {
        ErrorResponse response = ErrorResponse.of(
            ErrorCode.FURNITURE_NOT_FOUND,
            "가구를 찾을 수 없습니다."
        );

        assertFalse(response.success());
        assertEquals("FURNITURE_NOT_FOUND", response.code());
        assertEquals("가구를 찾을 수 없습니다.", response.message());
    }

    @Test
    void businessExceptionUsesDefaultMessage() {
        BusinessException exception = new BusinessException(
            ErrorCode.FURNITURE_NOT_FOUND
        );

        assertSame(ErrorCode.FURNITURE_NOT_FOUND, exception.errorCode());
        assertEquals(
            ErrorCode.FURNITURE_NOT_FOUND.defaultMessage(),
            exception.getMessage()
        );
    }

    @Test
    void businessExceptionCanUseCustomMessage() {
        BusinessException exception = new BusinessException(
            ErrorCode.FURNITURE_NOT_FOUND,
            "가구 ID 1을 찾을 수 없습니다."
        );

        assertEquals("가구 ID 1을 찾을 수 없습니다.", exception.getMessage());
    }

    @Test
    void handlerConvertsBusinessExceptionToHttpResponse() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        BusinessException exception = new BusinessException(
            ErrorCode.FURNITURE_NOT_FOUND
        );

        ResponseEntity<ErrorResponse> response =
            handler.handleBusinessException(exception);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().success());
        assertEquals("FURNITURE_NOT_FOUND", response.getBody().code());
        assertEquals(
            ErrorCode.FURNITURE_NOT_FOUND.defaultMessage(),
            response.getBody().message()
        );
    }

    @Test
    void handlerHidesUnexpectedExceptionDetails() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        ResponseEntity<ErrorResponse> response =
            handler.handleUnexpectedException(
                new IllegalStateException("DB password exposed")
            );

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("INTERNAL_SERVER_ERROR", response.getBody().code());
        assertEquals(
            ErrorCode.INTERNAL_SERVER_ERROR.defaultMessage(),
            response.getBody().message()
        );
    }
}
```

테스트를 실행한다.

```powershell
.\gradlew.bat test --tests "com.zimpic.backend.exception.CommonResponseTest"
```

이 테스트는 공통 객체와 Handler 메서드의 최소 동작만 검증한다. `@RestControllerAdvice`가 실제 HTTP 요청에서 적용되고 JSON이 원하는 형태로 반환되는지는 첫 Controller를 구현한 뒤 `MockMvc` 통합 테스트로 다시 확인한다.

완료 기준은 다음과 같다.

- 정상 응답이 `success=true`와 전달한 데이터를 보관한다.
- 실패 응답이 `success=false`, 오류 코드와 메시지를 보관한다.
- `BusinessException`이 기본 메시지와 직접 지정한 메시지를 구분한다.
- 업무 예외가 지정한 HTTP 상태와 오류 응답으로 변환된다.
- 예상하지 못한 예외의 원문이 사용자 응답에 포함되지 않는다.
- `CommonResponseTest`의 전체 테스트가 통과한다.

## 10. JPA 최소 연결 확인

처음부터 Django 모델 전체를 옮기지 않고 `Furniture` 하나로 JPA 연결을 검증한다.

최초 Entity에서 다음 항목을 확인한다.

- `Long` 타입 ID
- 문자열과 숫자 컬럼
- `BigDecimal` 타입의 `paddingCm`
- Boolean 컬럼
- Enum 문자열 저장
- unique와 null 제약조건

Django의 `DecimalField`는 `double`이 아니라 `BigDecimal`로 옮긴다. Enum은 순서가 아니라 문자열로 저장한다.

```java
@Enumerated(EnumType.STRING)
```

Repository를 만든 뒤 다음 동작을 확인한다.

1. 테스트용 Furniture 저장
2. ID로 조회
3. `nameEn`으로 조회
4. PostgreSQL에서 실제 행 확인

```sql
\dt
SELECT * FROM furniture;
```

개발 중에는 Entity 변경을 반영하기 위해 다음 설정을 사용한다.

```properties
spring.jpa.hibernate.ddl-auto=update
```

`update`는 로컬 개발에서만 사용한다. 운영 환경에서는 Flyway 같은 마이그레이션 도구와 `validate` 사용을 검토한다.

## 11. 전체 Entity와 테이블 구조 구현

기존 Django의 `policy`, `vision`, `estimates` 모델을 JPA Entity로 옮기고 PostgreSQL 전체 테이블 구조를 만든다. 이 단계에서는 테이블의 컬럼, 제약조건과 관계만 구현하며 실제 정책 행이나 사용자 데이터는 아직 추가하지 않는다.

> **DB 스키마 관리 원칙**
>
> - 개발 중에는 Entity를 수정하고 JPA의 `ddl-auto=update`를 이용해 로컬 DB 구조에 자동 반영한다.
> - 최초 배포 준비 단계에서 Flyway를 도입하고 `ddl-auto=validate`로 변경한다.
> - 운영 이후에는 Entity와 Flyway 마이그레이션 SQL을 함께 수정한다.
> - 운영 DB의 테이블 구조는 직접 수정하지 않고, Flyway가 배포 과정에서 변경하도록 한다.
> - 견적처럼 실행 중 생성되는 일반 데이터는 기존처럼 API와 JPA를 통해 조회·수정한다.

개발 중에는 Entity만 수정하면 되지만, 운영 이후에는 Java에서 사용할 객체 구조와 실제 PostgreSQL 구조가 모두 변경되어야 하므로 Entity 수정과 Flyway SQL 추가를 한 작업으로 처리한다. Flyway SQL은 `psql`에서 별도로 같은 DDL을 다시 실행하기 위한 파일이 아니라, 운영 DB에서 해당 DDL을 한 번만 실행하고 이력을 남기는 수단이다.

`backend/example/`은 Django 학습용 예제 앱이므로 Spring Boot 이전 대상에서 제외한다.

Entity는 외래키 의존관계를 고려해 다음 순서로 옮긴다.

### 정책 Entity

다른 기능에서 가구와 요금 정책을 참조하므로 가장 먼저 구현한다.

1. `Furniture`
2. `FurnitureRotation`
3. `BoxRule`
4. `TruckSpec`
5. `BasePrice`
6. `LadderFeeRule`
7. `StairsFeeRule`
8. `DistanceFeeRule`
9. `SpecialItemFee`

### 이미지와 탐지 Entity

`VisionDetection`이 `Furniture`를 참조하므로 정책 Entity 다음에 구현한다.

1. `VisionImage`
2. `VisionDetection`

### 견적 Entity

견적 항목이 `Furniture`를 참조하고 견적 방이 `VisionImage`를 참조하므로 마지막에 구현한다.

1. `Estimate`
2. `EstimateTruckPlan`
3. `EstimateRoom`
4. `EstimateItem`
5. `EstimatePrice`
6. `EstimatePriceSection`
7. `EstimatePriceDetail`

Django 모델을 옮길 때 다음 항목을 비교한다.

- 테이블과 컬럼 이름
- null 허용 여부와 기본값
- unique 및 복합 unique 제약조건
- 인덱스
- Enum 값
- 외래키와 삭제 정책
- 숫자의 최대 자릿수와 소수점 자리
- 기존 CSV 값과의 호환성

Entity 연관관계는 실제로 필요한 방향만 만들고 모든 관계를 양방향으로 만들지 않는다.

각 묶음을 구현할 때 Spring Boot를 실행한 뒤 PostgreSQL에서 생성된 테이블, 컬럼과 제약조건을 확인한다.

```sql
\dt
\d furniture
\d vision_image
\d estimate
```

전체 Entity 구현이 끝나면 정책 9개, 이미지·탐지 2개, 견적 7개로 총 18개 테이블이 생성되어야 한다. Django 모델의 `db_table` 이름을 유지해 기존 구조와 비교하기 쉽게 한다.

## 12. 개발용 정책 기준 데이터 적재

11번에서 생성한 정책 테이블에 견적 계산에 필요한 실제 기준 데이터를 넣는다. 기존 정책 CSV는 `backend/policy/data/`에 있으며, Django를 비교용으로 보존해야 하므로 원본 파일은 수정하거나 이동하지 않는다.

정책 Entity를 모두 작성하고 Spring Boot를 실행해 JPA 테이블 생성을 확인한 뒤, 기존 CSV를 기준으로 개발용 INSERT SQL을 작성한다. 이 SQL은 Java 패키지나 애플리케이션 리소스가 아니라 다음 위치에서 별도로 관리한다.

```text
spring-backend/db/seed/
├── 001_furniture.sql
├── 002_furniture_rotation.sql
├── 003_box_rule.sql
├── 004_truck_spec.sql
├── 005_base_price.sql
├── 006_ladder_fee_rule.sql
├── 007_stairs_fee_rule.sql
├── 008_distance_fee_rule.sql
└── 009_special_item_fee.sql
```

파일 이름 앞의 번호는 외래키 참조 관계에 맞는 실행 순서를 나타낸다. 각 SQL은 여러 번 실행하더라도 중복 데이터가 생기지 않도록 unique 제약조건과 `ON CONFLICT DO NOTHING`을 사용한다.

```sql
INSERT INTO furniture (name_en, name_kr)
VALUES ('bed', '침대')
ON CONFLICT (name_en) DO NOTHING;
```

Spring Boot가 테이블을 생성한 뒤 `spring-backend/`에서 SQL을 번호 순서대로 한 번 실행한다.

```bash
psql -h localhost -p 5432 -U zimpic_user -d zimpic \
  -v ON_ERROR_STOP=1 -f db/seed/001_furniture.sql
```

나머지 파일도 번호 순서대로 실행하고 각 테이블의 행 수를 확인한다.

```sql
SELECT COUNT(*) FROM furniture;
```

이 SQL은 로컬 기능 개발을 위한 임시 초기화 수단이다. 서버를 시작할 때마다 자동 실행하지 않으며, DB에서 수정한 정책값을 다시 덮어쓰지 않는다. 전체 로컬 테스트가 완료되면 22번의 안내에 따라 최종 스키마와 정책 데이터를 Flyway migration으로 옮긴다.

## 13. 정책 조회 API 이전

가장 먼저 이전할 실제 API는 가구 정책 조회로 한다.

```text
GET /api/policy/furniture/
```

기존 주소와 응답 필드명을 유지하면 React 연결을 바로 변경하지 않아도 된다.

다음 항목을 Django 응답과 비교한다.

- 필드 이름과 `snake_case` 형식
- 정렬 순서
- null 처리
- 숫자 표현
- 회전 가능 정보

## 14. FastAPI Health 호출

Spring Boot에서 FastAPI를 호출할 때는 Spring `RestClient`를 사용한다.

호출 대상은 다음과 같다.

```text
GET http://localhost:8000/health
```

FastAPI 주소는 코드에 직접 작성하지 않고 `yolo.base-url` 설정에서 가져온다.

다음 상황을 구분해 처리한다.

- 연결 실패
- 연결 타임아웃
- 응답 타임아웃
- 4xx 응답
- 5xx 응답
- JSON 변환 실패

FastAPI의 내부 예외나 응답 전체를 사용자에게 그대로 노출하지 않는다.

## 15. FastAPI 추론 API 호출

호출 형식은 다음과 같다.

```text
POST http://localhost:8000/inferences
Content-Type: multipart/form-data
파일 필드명: file
```

현재 FastAPI 응답 형식은 다음과 같다.

```json
{
  "model_version": "best_260204",
  "detections": [
    {
      "class_id": 0,
      "class_name": "example",
      "confidence": 0.95,
      "bbox": {
        "x": 0.5,
        "y": 0.5,
        "width": 0.2,
        "height": 0.3
      }
    }
  ]
}
```

응답 DTO는 다음 역할로 분리한다.

- `InferenceResponse`
- `DetectionResponse`
- `BoundingBoxResponse`

FastAPI의 `snake_case` JSON과 Java의 `camelCase` 필드가 정확하게 매핑되는지 테스트한다.

다음 실패 상황도 확인한다.

- FastAPI가 실행되지 않음
- 연결 또는 추론 시간 초과
- 지원하지 않는 이미지 형식
- 10MB를 초과한 이미지
- FastAPI 추론 실패
- 잘못된 JSON 응답

## 16. Vision 기능 이전

FastAPI 연결이 확인되면 기존 `/api/vision/` 흐름을 옮긴다.

```text
React 이미지 업로드
→ Spring Boot 파일 검증
→ 이미지 저장
→ FastAPI로 이미지 전달
→ 탐지 결과 수신
→ class_id로 Furniture 조회
→ VisionImage 저장
→ VisionDetection 저장
→ React 응답 반환
```

FastAPI 호출과 같이 오래 걸릴 수 있는 외부 요청 동안 DB 트랜잭션을 불필요하게 유지하지 않도록 처리 단계를 분리한다.

기존 React와 호환되도록 다음 주소와 응답 형식을 유지한다.

```text
POST /api/vision/
```

## 17. 견적 기능 이전

견적 기능은 다음 순서로 하나씩 옮긴다.

1. 요청 DTO와 검증
2. 견적 기본 정보 저장
3. 방과 가구 항목 저장
4. 가구 포장 크기 계산
5. 박스 개수 계산
6. 총 CBM 계산
7. 트럭 조합 계산
8. Kakao API 거리 조회
9. 기본요금 계산
10. 거리요금 계산
11. 계단요금 계산
12. 사다리차 요금 계산
13. 특수가구 요금 계산
14. 최종 금액과 세부 항목 저장
15. 기존 응답 형식으로 반환

계산 기능은 Controller가 아니라 각각의 Service에서 구현한다.

기능 하나를 옮길 때마다 동일한 입력을 Django와 Spring Boot에 전달해 결과를 비교한다. 금액, CBM과 추천 차량 등이 다르면 다음 기능으로 넘어가기 전에 원인을 확인한다.

기존 React와 호환되도록 다음 주소와 응답 형식을 유지한다.

```text
POST /api/estimates/
```

## 18. 테스트

### 단위 테스트

DB나 외부 API 없이 계산 로직을 검증한다.

- 포장 크기와 박스 개수
- CBM과 트럭 조합
- 기본 및 거리 요금
- 계단과 사다리차 요금
- 특수가구 요금

### Repository 테스트

- 저장과 조회
- unique와 외래키 제약조건
- Enum 문자열 저장
- Decimal 정밀도

### Controller 테스트

- 정상 요청과 응답
- 요청값 검증
- 잘못된 요청
- Service 예외 변환

### FastAPI Client 테스트

실제 YOLO 모델을 매번 실행하지 않고 가짜 HTTP 응답을 사용한다.

- 정상 JSON과 빈 탐지 목록
- 4xx와 5xx 응답
- 타임아웃
- 잘못된 JSON

### Django 비교 테스트

대표 입력을 고정해 두 백엔드 결과를 비교한다.

- 가구 정책 조회
- 이미지 탐지 결과 매핑
- 작은 이사 견적
- 여러 방이 포함된 견적
- 특수가구 포함 견적
- 계단 또는 사다리차 포함 견적
- 장거리 이사 견적

## 19. 작업 체크포인트

### 체크포인트 1. 프로젝트 실행

- Spring Boot 프로젝트 생성
- Gradle 테스트
- 기본 서버 실행

### 체크포인트 2. PostgreSQL 연결

- 환경변수 설정
- `/health` 구현
- DB 연결 확인

### 체크포인트 3. JPA 최소 검증

- Furniture Entity
- Repository
- 저장과 조회

### 체크포인트 4. 정책 데이터

- 전체 정책 Entity
- 개발용 정책 Seed SQL 수동 적재
- 가구 조회 API

### 체크포인트 5. FastAPI 연결

- `/health` 호출
- 추론 API 호출
- 타임아웃과 오류 처리

### 체크포인트 6. Vision 이전

- 이미지 저장
- 탐지 결과 매핑과 저장
- `/api/vision/` 호환

### 체크포인트 7. 견적 이전

- 계산 로직 단위별 이전
- `/api/estimates/` 호환
- Django 결과 비교

## 20. 완료 기준

- Java 21과 Spring Boot 4.1.1로 실행된다.
- PostgreSQL의 `zimpic` DB를 사용한다.
- 정책 테이블과 초기 데이터가 생성된다.
- `/health`가 애플리케이션과 DB 상태를 반영한다.
- Spring Boot가 FastAPI의 health와 추론 API를 호출한다.
- 탐지 결과를 Furniture와 연결해 저장한다.
- 기존 정책, Vision과 견적 API 형식을 유지한다.
- 대표 입력에서 Django와 핵심 계산 결과가 일치한다.
- DB 비밀번호와 Kakao API 키가 Git에 포함되지 않는다.
- 기존 Django 백엔드는 비교가 끝날 때까지 보존된다.

## 21. 추가로 결정할 사항

기존 Django에는 정책을 수정할 수 있는 Admin 기능이 있지만 현재 로드맵에는 Spring Boot 전환 이후의 관리자 기능이 정의되어 있지 않다.

초기 Spring Boot 이전에서는 정책 조회와 개발용 Seed SQL 적재까지만 구현한다. Django를 제거하기 전에는 별도 관리자 화면, 제한된 관리 API 또는 다른 운영 방식을 결정해야 한다.

## 22. 배포 전 Flyway 전환

이 문서의 로컬 기능 개발 단계에서는 `ddl-auto=update`로 JPA가 테이블을 만들고 `db/seed/*.sql`을 한 번 수동 실행한다. React까지 연결한 전체 로컬 테스트가 끝나면 Docker 이미지를 만들기 전에 로드맵 6번에서 Flyway로 전환한다.

전환할 때 다음 작업을 수행한다.

1. 최종 Entity와 PostgreSQL 테이블 구조를 비교한다.
2. Flyway 의존성을 추가한다.
3. 최종 테이블 생성 SQL을 `V1__create_tables.sql`로 작성한다.
4. 정책 초기 데이터 SQL을 `V2__insert_policy_data.sql`로 작성한다.
5. `ddl-auto`를 `update`에서 `validate`로 변경한다.
6. 빈 로컬 DB에서 Flyway가 V1과 V2를 순서대로 한 번만 실행하는지 확인한다.
7. 새로 구성된 DB에서 전체 API와 사용자 흐름을 다시 테스트한다.

```text
spring-backend/src/main/resources/db/migration/
├── V1__create_tables.sql
└── V2__insert_policy_data.sql
```

Flyway 전환의 세부 명령과 검증 방법은 해당 단계에서 별도의 `detail/06-flyway.md` 문서로 작성한다.
