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

Entity의 Enum은 `@Enumerated(EnumType.STRING)`으로 저장한다. 기존 Django의 트럭 값은 `1T`, `2_5T` 형식이지만 Java Enum 상수는 숫자로 시작할 수 없으므로 Spring Boot와 신규 DB에서는 `T1`, `T2_5` 형식으로 통일한다. 이후 프론트 요청·응답 값이 이 형식과 다르면 Entity 저장 형식을 바꾸지 않고 DTO 매핑 단계에서 변환한다.

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
    // 발생한 업무 오류의 종류와 HTTP 상태를 보관한다.
    private final ErrorCode errorCode;

    // 별도 메시지가 없으면 ErrorCode의 기본 메시지를 사용한다.
    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.defaultMessage());
    }

    // 상황에 맞는 상세 메시지를 직접 전달할 때 사용한다.
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    // GlobalExceptionHandler가 오류 코드를 조회할 때 사용한다.
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

// 모든 REST Controller에서 발생한 예외를 공통으로 처리한다.
@RestControllerAdvice
public class GlobalExceptionHandler {
    // 예상하지 못한 예외의 상세 내용은 서버 로그에 기록한다.
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // Service 등에서 의도적으로 발생시킨 업무 예외를 처리한다.
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

    // 별도로 처리하지 않은 모든 예외를 공통 500 응답으로 변환한다.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(
        Exception exception
    ) {
        // 실제 예외 원문은 사용자 응답에 넣지 않고 로그에만 남긴다.
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

테스트 코드는 운영 애플리케이션에 포함되는 기능이 아니라 작성한 코드가 예상대로 동작하는지 자동으로 확인하는 코드다. `src/test/java` 아래에 작성하며 `gradlew test`를 실행할 때 JUnit 5가 찾아서 실행한다.

- `@Test`: 해당 메서드를 실행할 테스트로 표시한다.
- `assertEquals(expected, actual)`: 예상값과 실제 값의 내용이 같은지 확인한다.
- `assertTrue()`·`assertFalse()`: 값이 각각 `true` 또는 `false`인지 확인한다.
- `assertNotNull()`: 응답 본문과 같은 값이 `null`이 아닌지 확인한다.
- `assertSame()`: 두 값이 내용만 같은 것이 아니라 동일한 객체인지 확인한다. 하나의 객체로 관리되는 enum 비교에 사용할 수 있다.
- `import static`: `Assertions.assertEquals()` 대신 `assertEquals()`처럼 클래스 이름을 생략해 호출할 수 있게 한다.

각 테스트는 보통 테스트할 객체를 준비하고 실행한 다음 `assert` 메서드로 결과를 검증하는 순서로 작성한다. 검증값 하나라도 예상과 다르면 해당 테스트와 Gradle의 `test` 작업이 실패한다.

```text
준비(Arrange) → 실행(Act) → 검증(Assert)
```

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

    // 정상 응답이 성공 여부와 전달한 데이터를 그대로 보관하는지 확인한다.
    @Test
    void successResponseContainsData() {
        ApiResponse<String> response = ApiResponse.success("data");

        assertTrue(response.success());
        assertEquals("data", response.data());
    }

    // 실패 응답이 오류 코드와 사용자 메시지를 올바르게 만드는지 확인한다.
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

    // 메시지를 생략하면 ErrorCode의 기본 메시지가 사용되는지 확인한다.
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

    // 업무 상황에 맞는 메시지를 직접 지정할 수 있는지 확인한다.
    @Test
    void businessExceptionCanUseCustomMessage() {
        BusinessException exception = new BusinessException(
            ErrorCode.FURNITURE_NOT_FOUND,
            "가구 ID 1을 찾을 수 없습니다."
        );

        assertEquals("가구 ID 1을 찾을 수 없습니다.", exception.getMessage());
    }

    // 업무 예외가 지정된 HTTP 상태와 실패 응답으로 변환되는지 확인한다.
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

    // 예상하지 못한 예외의 원문을 숨기고 공통 500 응답을 반환하는지 확인한다.
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

이 단계의 목적은 전체 모델을 옮기는 것이 아니라 다음 흐름이 실제 PostgreSQL에서 동작하는지 먼저 확인하는 것이다.

```text
Furniture Entity
  → FurnitureRepository
  → Hibernate가 SQL 생성
  → PostgreSQL furniture 테이블 저장·조회
```

### 최신 문서와 기존 Django 모델 확인

기존 모델은 `backend/policy/models.py`의 `Furniture`다. 다만 리팩터링 과정에서 정책이 갱신됐을 수 있으므로 Spring Boot 구현은 `docs/07-db-schema.md`의 `furniture` 구조와 `docs/05-furniture-data.md`의 정책값을 최신 기준으로 사용한다. Django 모델은 기존 동작을 비교하고 빠진 필드를 찾는 참고 자료로 사용한다.

기준이 서로 다르면 다음 우선순위로 결정한다.

```text
최신 정책·스키마 문서 → 현재 CSV 기준 데이터 → 기존 Django 모델
```

| Django | Java·JPA | PostgreSQL | 제약 |
| --- | --- | --- | --- |
| `BigAutoField` | `Long` | `BIGINT` | PK, 자동 증가 |
| `CharField(choices=...)` | `FurnitureCategory` | `VARCHAR(20)` | NOT NULL |
| `IntegerField` | `Integer` | `INTEGER` | NULL 허용, UNIQUE |
| `CharField` | `String` | `VARCHAR` | 길이와 NULL 여부 지정 |
| `PositiveIntegerField` | `Integer` | `INTEGER` | 1 이상 |
| `DecimalField(4, 1)` | `BigDecimal` | `DECIMAL(4,1)` | 0.0~10.0 |
| `BooleanField` | `boolean` | `BOOLEAN` | NOT NULL |

최초 Entity에서 다음 항목을 확인한다.

- `Long` 타입 ID
- 문자열과 숫자 컬럼
- `BigDecimal` 타입의 `paddingCm`
- Boolean 컬럼
- Enum 문자열 저장
- unique와 null 제약조건

Django 모델의 `padding_cm` 기본값은 `0.0`이지만 최신 DB 스키마 문서에는 `1.0`으로 정의되어 있다. Spring Boot에서는 최신 문서를 따라 PostgreSQL 컬럼의 기본값을 `1.0`으로 사용한다. Java 코드에는 현재 사용하는 `0.0`, `0.5`, `1.0` 등의 값을 enum이나 상수 목록으로 정의하지 않고 `BigDecimal` 타입과 `0.0~10.0` 범위만 정의한다. 실제 가구별 값은 CSV에서 전달하며, 이사 박스처럼 포장이 필요 없는 항목의 `0.0`도 CSV 값 그대로 보존한다.

### FurnitureCategory 작성

다음 파일에 Django의 `FurnitureCategory`와 동일한 enum 상수를 작성한다.

```text
src/main/java/com/zimpic/backend/entity/type/FurnitureCategory.java
```

```java
package com.zimpic.backend.entity.type;

public enum FurnitureCategory {
    GENERAL_APPLIANCE,
    KITCHEN_APPLIANCE,
    GENERAL_FURNITURE,
    SPECIAL_FURNITURE,
    ETC
}
```

Java enum 상수 이름을 Django와 동일하게 유지하면 별도 변환기 없이 문자열 그대로 저장할 수 있다.

### Furniture Entity 작성

다음 파일에 Entity를 작성한다.

```text
src/main/java/com/zimpic/backend/entity/Furniture.java
```

```java
package com.zimpic.backend.entity;

import java.math.BigDecimal;

import com.zimpic.backend.entity.type.FurnitureCategory;

import org.hibernate.annotations.ColumnDefault;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
    name = "furniture",
    indexes = {
        @Index(name = "idx_furniture_category", columnList = "category")
    }
)
public class Furniture {

    // PostgreSQL BIGINT 자동 증가 PK
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // enum의 순서가 아니라 GENERAL_APPLIANCE 같은 이름을 저장한다.
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 20)
    private FurnitureCategory category;

    // YOLO가 매핑되지 않는 가구도 있으므로 null을 허용한다.
    @Column(name = "yolo_id", unique = true)
    private Integer yoloId;

    @NotBlank
    @Column(name = "name_en", nullable = false, unique = true, length = 64)
    private String nameEn;

    @NotBlank
    @Column(name = "name_kr", nullable = false, length = 64)
    private String nameKr;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "reference_model", length = 255)
    private String referenceModel;

    @Column(name = "reference_url", length = 500)
    private String referenceUrl;

    @Positive
    @Column(name = "width_cm", nullable = false)
    private Integer widthCm;

    @Positive
    @Column(name = "depth_cm", nullable = false)
    private Integer depthCm;

    @Positive
    @Column(name = "height_cm", nullable = false)
    private Integer heightCm;

    // DECIMAL(4,1): 전체 네 자리 중 소수점 아래 한 자리를 사용한다.
    @NotNull
    @DecimalMin("0.0")
    @DecimalMax("10.0")
    @ColumnDefault("1.0")
    @Column(name = "padding_cm", nullable = false, precision = 4, scale = 1)
    private BigDecimal paddingCm;

    @ColumnDefault("false")
    @Column(name = "needs_disassembly", nullable = false)
    private boolean needsDisassembly;

    @ColumnDefault("false")
    @Column(name = "stackable", nullable = false)
    private boolean stackable;

    @ColumnDefault("false")
    @Column(name = "can_stack_on_top", nullable = false)
    private boolean canStackOnTop;

    // id는 DB가 만들기 때문에 생성자에서 받지 않는다.
    public Furniture(
        FurnitureCategory category,
        Integer yoloId,
        String nameEn,
        String nameKr,
        String description,
        String referenceModel,
        String referenceUrl,
        Integer widthCm,
        Integer depthCm,
        Integer heightCm,
        BigDecimal paddingCm,
        boolean needsDisassembly,
        boolean stackable,
        boolean canStackOnTop
    ) {
        this.category = category;
        this.yoloId = yoloId;
        this.nameEn = nameEn;
        this.nameKr = nameKr;
        this.description = description;
        this.referenceModel = referenceModel;
        this.referenceUrl = referenceUrl;
        this.widthCm = widthCm;
        this.depthCm = depthCm;
        this.heightCm = heightCm;
        this.paddingCm = paddingCm;
        this.needsDisassembly = needsDisassembly;
        this.stackable = stackable;
        this.canStackOnTop = canStackOnTop;
    }
}
```

주요 애너테이션의 역할은 다음과 같다.

- `@Entity`: 이 클래스를 JPA가 관리하는 Entity로 등록한다.
- `@Table(name = "furniture")`: 기존 Django 테이블 이름을 그대로 사용한다.
- `@Id`: 기본 키 필드를 지정한다.
- `@GeneratedValue(strategy = IDENTITY)`: PostgreSQL이 ID를 자동 생성하게 한다.
- `@Column`: 실제 컬럼 이름, 길이, NULL과 UNIQUE 제약조건을 지정한다.
- `@Enumerated(EnumType.STRING)`: enum 순서인 숫자가 아니라 상수 이름을 저장한다.
- `@ColumnDefault`: Hibernate가 개발용 테이블을 만들 때 PostgreSQL 컬럼 기본값을 DDL에 반영한다.
- `@NotBlank`, `@Positive`, `@DecimalMin`, `@DecimalMax`: 빈 이름과 Django validator의 값 범위를 검증한다.
- `@Getter`: 각 필드의 조회 메서드를 Lombok이 생성한다.
- `@NoArgsConstructor(PROTECTED)`: JPA에 필요한 기본 생성자를 외부에서 함부로 사용하지 못하도록 생성한다.

Django의 `DecimalField`는 `double`이 아니라 `BigDecimal`로 옮긴다. 금액과 소수 자릿수가 정해진 값은 이진 부동소수점 오차가 없는 `BigDecimal`을 사용한다. `@DecimalMin`과 `@DecimalMax`는 허용 범위만 검사하고, 실제 포장 두께는 CSV에서 읽어 생성자에 전달한다. 새로운 두께가 추가되어도 범위 안의 값이면 Java enum이나 상수를 수정할 필요가 없다.

`@ColumnDefault("1.0")`은 값을 직접 전달하지 않는 SQL을 위한 PostgreSQL 기본값을 표현한다. JPA로 `Furniture`를 생성할 때는 `@NotNull`에 따라 `paddingCm`을 반드시 전달하며, Java 코드에서 누락된 값을 임의로 `1.0`으로 바꾸지 않는다. 이렇게 해야 CSV 누락이나 잘못된 데이터가 조용히 기본값으로 저장되는 것을 막을 수 있다.

`name_en`은 `unique=true`로 설정하면 PostgreSQL이 조회 가능한 unique index를 함께 만들기 때문에 별도의 동일 컬럼 index는 중복해서 만들지 않는다. `category`는 목록 필터링에 사용하므로 명시적인 index를 둔다.

### FurnitureRepository 작성

다음 디렉터리와 파일을 만든다.

```text
src/main/java/com/zimpic/backend/repository/FurnitureRepository.java
```

```java
package com.zimpic.backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.zimpic.backend.entity.Furniture;

public interface FurnitureRepository extends JpaRepository<Furniture, Long> {
    Optional<Furniture> findByNameEn(String nameEn);

    Optional<Furniture> findByYoloId(Integer yoloId);
}
```

`JpaRepository<Furniture, Long>`에서 `Furniture`는 관리할 Entity, `Long`은 기본 키 타입이다. 별도 구현 클래스를 만들지 않아도 `save()`, `findById()`, `findAll()`과 `delete()` 같은 기본 메서드를 사용할 수 있다.

`findByNameEn()`과 `findByYoloId()`는 Spring Data JPA가 메서드 이름을 분석해 각각 `name_en`, `yolo_id` 조건의 조회 SQL을 만든다. 결과가 없을 수 있으므로 반환 타입은 `Optional<Furniture>`로 한다.

### 실제 PostgreSQL Repository 테스트

이 테스트는 메모리 DB가 아니라 `application.properties`에 설정한 로컬 PostgreSQL을 사용한다. 테스트가 끝나면 `@Transactional`이 변경 내용을 롤백하므로 테스트용 가구가 DB에 남지 않는다.

다음 파일을 만든다.

```text
src/test/java/com/zimpic/backend/repository/FurnitureRepositoryTest.java
```

```java
package com.zimpic.backend.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.zimpic.backend.entity.Furniture;
import com.zimpic.backend.entity.type.FurnitureCategory;

@SpringBootTest
@Transactional
class FurnitureRepositoryTest {

    @Autowired
    private FurnitureRepository furnitureRepository;

    @Test
    void savesAndFindsFurniture() {
        // 준비: 기존 CSV와 같은 형태의 테스트용 가구를 만든다.
        Furniture furniture = new Furniture(
            FurnitureCategory.GENERAL_FURNITURE,
            9999,
            "jpa_test_furniture",
            "JPA 테스트 가구",
            "Repository 연결 확인용 데이터",
            null,
            null,
            100,
            50,
            80,
            new BigDecimal("1.0"),
            false,
            true,
            true
        );

        // 실행: INSERT SQL을 즉시 실행하고 생성된 ID를 받는다.
        Furniture saved = furnitureRepository.saveAndFlush(furniture);

        // 검증: ID 자동 생성과 name_en 기반 조회를 확인한다.
        assertNotNull(saved.getId());

        Furniture found = furnitureRepository
            .findByNameEn("jpa_test_furniture")
            .orElseThrow();

        assertEquals(saved.getId(), found.getId());
        assertEquals("JPA 테스트 가구", found.getNameKr());
        assertEquals(new BigDecimal("1.0"), found.getPaddingCm());
        assertTrue(found.isStackable());
    }
}
```

`@SpringBootTest`는 실제 Spring 설정과 DataSource를 불러오고, `@Autowired`는 Spring이 만든 Repository 객체를 테스트에 주입한다. `saveAndFlush()`는 저장을 영속성 컨텍스트에만 미루지 않고 PostgreSQL에 INSERT SQL을 즉시 전달한다.

테스트용 `yolo_id`와 `name_en`은 기존 데이터의 unique 값과 충돌하지 않는 값을 사용한다. 테스트가 중간에 비정상 종료되어 데이터가 남았다면 PostgreSQL에서 해당 행을 삭제한 뒤 다시 실행한다.

### 터미널에서 Repository 테스트 실행

VS Code나 IntelliJ의 실행 버튼을 사용하지 않아도 터미널에서 Repository 테스트만 실행할 수 있다. PostgreSQL을 실행한 뒤 저장소 루트에서 다음 명령을 실행한다.

```powershell
cd spring-backend
$env:DB_PASSWORD="로컬에서_설정한_비밀번호"
.\gradlew.bat test --tests "com.zimpic.backend.repository.FurnitureRepositoryTest"
```

테스트가 통과하면 마지막에 `BUILD SUCCESSFUL`이 표시된다. 실패하면 터미널에 표시된 첫 번째 테스트 오류와 PostgreSQL 연결 상태, `DB_PASSWORD` 값을 확인한다.

### PostgreSQL 테이블 확인

애플리케이션이나 Repository 테스트를 처음 실행하면 `ddl-auto=update` 설정에 따라 Hibernate가 `furniture` 테이블을 생성하거나 기존 구조를 갱신한다.

`psql`로 접속한다.

```powershell
psql -h 127.0.0.1 -p 5432 -U zimpic_user -d zimpic
```

테이블과 컬럼을 확인한다.

```sql
-- 현재 DB에서 furniture 테이블이 존재하는지 확인
\dt furniture

-- furniture 테이블의 컬럼, 타입, 제약조건과 인덱스 확인
\d furniture
```

`@Transactional` 테스트가 끝나면 테스트에서 저장한 행은 롤백되지만, Hibernate가 생성한 테이블과 컬럼 구조는 삭제되지 않는다.

다음 항목을 확인한다.

- `id`가 `BIGINT` 기본 키와 자동 증가로 생성됨
- `category`가 `VARCHAR(20)`으로 생성됨
- `yolo_id`와 `name_en`에 unique 제약조건이 있음
- `padding_cm`이 `NUMERIC(4,1)`로 생성됨
- `padding_cm`의 DB 기본값이 `1.0`으로 생성됨
- 세 Boolean 컬럼의 DB 기본값이 `false`로 생성됨
- 필수 컬럼에 `NOT NULL`이 적용됨
- `category` index가 생성됨

`@ColumnDefault`는 새 테이블을 생성할 때의 DDL에 기본값을 넣는다. 이미 `furniture` 테이블을 만든 뒤 기본값만 변경했다면 `ddl-auto=update`가 기존 컬럼의 기본값까지 항상 고쳐준다고 가정하지 말고 `\d furniture` 결과를 확인한다. 기존 로컬 개발 테이블의 기본값이 다르면 다음 SQL로 최신 문서와 맞춘다.

```sql
ALTER TABLE furniture
    ALTER COLUMN padding_cm SET DEFAULT 1.0,
    ALTER COLUMN needs_disassembly SET DEFAULT false,
    ALTER COLUMN stackable SET DEFAULT false,
    ALTER COLUMN can_stack_on_top SET DEFAULT false;
```

이 SQL은 현재 로컬 개발 DB를 맞추기 위한 임시 조치다. 배포용 최종 기본값은 21번에서 Flyway migration으로 확정한다. Java에서 Entity를 생성할 때는 CSV나 요청에서 받은 `paddingCm` 값을 명시적으로 전달한다.

테스트는 트랜잭션을 롤백하므로 정상 완료 후 `jpa_test_furniture` 행이 남아 있지 않아야 한다.

```sql
SELECT *
FROM furniture
WHERE name_en = 'jpa_test_furniture';
```

예상 결과는 0행이다. 테스트 실행 중에는 실제 INSERT와 SELECT가 수행되지만 테스트 종료 시 롤백된다.

개발 중에는 Entity 변경을 반영하기 위해 다음 설정을 사용한다.

```properties
spring.jpa.hibernate.ddl-auto=update
```

`update`는 로컬 개발에서만 사용한다. 운영 환경에서는 Flyway 같은 마이그레이션 도구와 `validate` 사용을 검토한다.

완료 기준은 다음과 같다.

- `FurnitureCategory`가 기존 Django 값과 동일하다.
- `Furniture`의 컬럼명, 타입, 길이와 제약조건이 기존 모델과 대응한다.
- `FurnitureRepository`가 애플리케이션 시작 시 정상 등록된다.
- 테스트용 Furniture의 저장, ID 자동 생성과 `nameEn` 조회가 성공한다.
- `FurnitureRepositoryTest`가 통과하고 테스트 데이터가 롤백된다.
- PostgreSQL에서 `furniture` 테이블 구조를 확인할 수 있다.

## 11. 전체 Entity와 테이블 구조 구현

기존 Django의 `policy`, `vision`, `estimates` 모델을 JPA Entity로 옮기고 PostgreSQL 전체 테이블 구조를 만든다. 이 단계에서는 테이블의 컬럼, 제약조건과 관계만 구현하며 실제 정책 행이나 사용자 데이터는 아직 추가하지 않는다.

`Furniture`를 제외한 17개 Entity의 전체 코드와 작성 순서는 [04-1. JPA Entity 구현](04-1-jpa-entities.md)에서 별도로 다룬다. 이 절은 전체 구조와 완료 기준을 설명하고, 실제 입력은 04-1 문서를 따라 진행한다.

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

### [정책 Entity → 04-1 문서 3번](04-1-jpa-entities.md#3-정책-entity-8개)

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

### [이미지와 탐지 Entity → 04-1 문서 4번](04-1-jpa-entities.md#4-이미지탐지-entity-2개)

`VisionDetection`이 `Furniture`를 참조하므로 정책 Entity 다음에 구현한다.

1. `VisionImage`
2. `VisionDetection`

### [견적 Entity → 04-1 문서 5번](04-1-jpa-entities.md#5-견적-entity-7개)

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
├── run-seed.ps1
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

파일 이름 앞의 번호는 외래키 참조 관계에 맞는 실행 순서를 나타낸다. 각 SQL은 여러 번 실행하더라도 중복 데이터가 생기지 않도록 unique 제약조건과 `ON CONFLICT DO NOTHING` 또는 동일 구간 존재 여부 확인을 사용한다. CSV의 트럭 타입은 신규 Spring Enum과 DB 형식에 맞춰 `1T`를 `T1`, `2_5T`를 `T2_5`처럼 변환한다.

```sql
INSERT INTO furniture (name_en, name_kr)
VALUES ('bed', '침대')
ON CONFLICT (name_en) DO NOTHING;
```

### Windows 개발 환경에서 Seed 실행

이 단계는 Windows PowerShell을 기준으로 진행한다. 먼저 PostgreSQL이 실행 중이고 `psql` 명령을 사용할 수 있는지 확인한다.

```powershell
psql --version
```

저장소 루트에서 `spring-backend`로 이동한 뒤 실행 스크립트를 호출한다.

```powershell
cd spring-backend
.\db\seed\run-seed.ps1
```

저장소 루트에서 이동하지 않고 실행하려면 다음 경로를 사용한다.

```powershell
.\spring-backend\db\seed\run-seed.ps1
```

`run-seed.ps1`은 001~009 SQL 파일을 외래키 순서대로 찾아 한 번의 `psql` 세션에서 실행한다. Windows 기본 인코딩과 한글 SQL의 충돌을 방지하기 위해 실행 중 `PGCLIENTENCODING=UTF8`도 자동 적용한다. 비밀번호를 물으면 `zimpic_user`의 PostgreSQL 비밀번호를 입력한다. PowerShell에서 비밀번호 입력 문자가 화면에 표시되지 않는 것은 정상이다.

PowerShell 실행 정책 때문에 스크립트를 실행할 수 없다는 오류가 발생하면 현재 터미널 프로세스에서만 스크립트 실행을 허용한 뒤 다시 실행한다.

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
.\db\seed\run-seed.ps1
```

`Scope Process` 설정은 현재 PowerShell 창에만 적용되며 창을 닫으면 사라진다. 시스템 전체 실행 정책은 변경하지 않는다.

기본값과 다른 DB에 적재할 때는 실행 옵션을 전달한다.

```powershell
.\db\seed\run-seed.ps1 -DatabaseHost localhost -Port 5432 -Database zimpic -Username zimpic_user
```

기본 접속값은 `localhost:5432`, 데이터베이스 `zimpic`, 사용자 `zimpic_user`다. 스크립트는 `--single-transaction`과 `ON_ERROR_STOP`을 사용하므로 중간 SQL에서 오류가 발생하면 이후 파일을 실행하지 않고 전체 INSERT를 롤백한다. 마지막에 `Seed data loaded successfully.`가 표시되면 정상 완료된 것이다.

최초 실행에 성공하면 다음과 비슷한 결과가 표시된다.

```text
Loading Seed SQL files 001-009 into database 'zimpic'.
zimpic_user 사용자의 암호:

INSERT 0 41
INSERT 0 104
INSERT 0 10
INSERT 0 3
INSERT 0 9
INSERT 0 64
INSERT 0 2
INSERT 0 6
INSERT 0 10
Seed data loaded successfully.
```

각 `INSERT 0 N`의 `N`은 001~009 SQL이 추가한 행 수다. 위 결과는 CSV의 가구 41개, 회전 정보 104개와 나머지 정책 데이터가 모두 정상 적재됐다는 의미다.

전체 파일 실행 후 각 테이블의 행 수를 확인한다.

```sql
SELECT 'furniture' AS table_name, COUNT(*) FROM furniture
UNION ALL SELECT 'furniture_rotation', COUNT(*) FROM furniture_rotation
UNION ALL SELECT 'policy_box_rule', COUNT(*) FROM policy_box_rule
UNION ALL SELECT 'policy_truck_spec', COUNT(*) FROM policy_truck_spec
UNION ALL SELECT 'policy_base_price', COUNT(*) FROM policy_base_price
UNION ALL SELECT 'policy_ladder_fee_rule', COUNT(*) FROM policy_ladder_fee_rule
UNION ALL SELECT 'policy_stairs_fee_rule', COUNT(*) FROM policy_stairs_fee_rule
UNION ALL SELECT 'policy_distance_fee_rule', COUNT(*) FROM policy_distance_fee_rule
UNION ALL SELECT 'policy_special_item_fee', COUNT(*) FROM policy_special_item_fee;
```

현재 CSV 기준 예상 행 수는 순서대로 `41, 104, 10, 3, 9, 64, 2, 6, 10`이다. `run-seed.ps1`을 다시 실행해도 이 행 수가 증가하지 않아야 한다.

이 SQL은 로컬 기능 개발을 위한 임시 초기화 수단이다. 서버를 시작할 때마다 자동 실행하지 않으며, DB에서 수정한 정책값을 다시 덮어쓰지 않는다. 전체 로컬 테스트가 완료되면 21번의 안내에 따라 최종 스키마와 정책 데이터를 Flyway migration으로 옮긴다.

## 13. API 이전

Entity와 정책 데이터 준비가 끝났으므로 기존 Django가 처리하던 HTTP API를 Spring Boot로 옮긴다. 현재 프론트가 실제로 호출하는 업무 API 묶음은 다음 세 개다.

| 기능 | 메서드와 주소 | Spring 이전 단계 |
| --- | --- | --- |
| 가구 정책 목록 | `GET /api/policy/furniture/` | 13번 |
| 이미지 탐지 | `POST /api/vision/` | 15번 |
| 견적 생성 | `POST /api/estimates/` | 16번 |

Django의 `FurnitureViewSet`은 목록 외에 `GET /api/policy/furniture/{id}/` 단건 조회도 자동 제공한다. 하지만 현재 프론트는 목록, 이미지 탐지, 견적 생성 세 API만 호출한다. 이 단계에서는 실제로 사용하는 가구 목록 조회만 구현하고 단건 조회는 필요할 때 추가한다.

API 하나를 이전할 때 다음 순서로 구현한다.

```text
기존 Django 요청·응답 확인
→ Repository 조회 작성
→ 요청·응답 DTO 작성
→ Service 작성
→ Controller 작성
→ 자동 테스트
→ 실제 HTTP 호출과 Django 결과 비교
```

### 첫 번째 이전 예제: 정책 조회 API

가장 먼저 다음 가구 정책 목록 API를 이전한다.

```text
GET /api/policy/furniture/
```

이 API는 요청 본문과 외부 API 호출이 없는 단순한 GET 요청이다. 따라서 Vision과 견적처럼 복잡한 기능을 옮기기 전에 `Repository → DTO → Service → Controller → JSON 응답`의 기본 흐름과 PostgreSQL 연결을 테스트하기 적합하다.

기존 주소와 응답 필드명을 유지해 React 연결을 바로 변경하지 않아도 되도록 한다. 이 단계에서 정책 조회 API를 완성하면 이후 Vision과 견적 API도 같은 계층 구조를 기준으로 구현한다.

### 기존 Django 응답 확인

기존 `FurnitureSerializer`는 `yolo_id`, `padding_cm`, `stackable`, `can_stack_on_top`을 응답에서 제외한다. `FurnitureRotation`도 현재 Serializer에 포함되지 않으므로 이 API 응답에는 회전 정보가 없다. Spring Boot도 우선 현재 응답과 동일한 필드만 반환한다.

```json
[
  {
    "id": 1,
    "category": "GENERAL_APPLIANCE",
    "nameEn": "tv_base",
    "nameKr": "TV(일반)",
    "description": "일반적인 모니터와 TV를 기준으로 계산했어요.",
    "referenceModel": "LG PC 모니터 80cm",
    "referenceUrl": "https://example.com/product",
    "widthCm": 73,
    "depthCm": 5,
    "heightCm": 43,
    "needsDisassembly": false
  }
]
```

목록은 `id` 오름차순으로 정렬하고 `ETC` 카테고리는 제외한다. nullable 컬럼인 설명, 기준 제품과 URL은 값이 없으면 JSON `null`로 반환한다. 기존 API 호환을 위해 이 API에는 `ApiResponse` 공통 래퍼를 적용하지 않고 배열을 바로 반환한다.

### FurnitureRepository 조회 추가

`FurnitureRepository`에 다음 메서드를 추가한다.

```java
import java.util.List;
import java.util.Optional;

import com.zimpic.backend.entity.type.FurnitureCategory;

public interface FurnitureRepository extends JpaRepository<Furniture, Long> {
    Optional<Furniture> findByNameEn(String nameEn);

    Optional<Furniture> findByYoloId(Integer yoloId);

    List<Furniture> findAllByCategoryNotOrderByIdAsc(FurnitureCategory category);
}
```

Spring Data JPA가 메서드 이름을 분석해 전달받은 카테고리를 제외하고 ID 오름차순으로 조회한다.

예를 들어 아래처럼 호출하면 `ETC`가 아닌 가구만 조회하며, SQL의 `category <> 'ETC' ORDER BY id ASC`와 같은 의미다.

```java
List<Furniture> furnitureList =
    furnitureRepository.findAllByCategoryNotOrderByIdAsc(FurnitureCategory.ETC);
```

여기서 `Not`은 메서드 호출 시 전달한 값과 같지 않은 항목을 조회한다는 뜻이다. 다른 카테고리를 전달하면 그 카테고리가 제외된다.

### FurnitureResponse 작성

다음 파일을 만든다.

```text
src/main/java/com/zimpic/backend/dto/response/FurnitureResponse.java
```

Java 필드명과 JSON 응답 필드명은 Spring Boot와 React에서 일반적으로 사용하는 `camelCase`로 통일한다.

```java
package com.zimpic.backend.dto.response;

import com.zimpic.backend.entity.Furniture;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class FurnitureResponse {
    private final Long id;
    private final String category;
    private final String nameEn;
    private final String nameKr;
    private final String description;
    private final String referenceModel;
    private final String referenceUrl;
    private final Integer widthCm;
    private final Integer depthCm;
    private final Integer heightCm;
    private final boolean needsDisassembly;

    public static FurnitureResponse entityToDto(Furniture furniture) {
        return new FurnitureResponse(
            furniture.getId(),
            furniture.getCategory().name(),
            furniture.getNameEn(),
            furniture.getNameKr(),
            furniture.getDescription(),
            furniture.getReferenceModel(),
            furniture.getReferenceUrl(),
            furniture.getWidthCm(),
            furniture.getDepthCm(),
            furniture.getHeightCm(),
            furniture.isNeedsDisassembly()
        );
    }
}
```

`@Getter`는 각 필드의 조회 메서드를 만들고, `@AllArgsConstructor`는 모든 필드를 받는 생성자를 만든다. 응답 DTO는 생성 후 값을 변경할 필요가 없으므로 필드를 `final`로 선언하고 `@Setter`는 사용하지 않는다. 별도의 Jackson 이름 설정이 없으면 `nameEn`, `widthCm`, `needsDisassembly`와 같은 Java 필드명이 JSON에도 그대로 사용된다.

기존 프론트에서 Django 응답의 `name_en`, `width_cm`, `needs_disassembly`와 같은 `snake_case` 필드를 참조하고 있다면, Spring Boot API로 전환할 때 각각 `nameEn`, `widthCm`, `needsDisassembly`와 같은 `camelCase`로 수정해야 한다. 이후 작성하는 요청·응답 DTO도 특별한 외부 API 규격이 없는 한 `@JsonProperty` 없이 `camelCase`를 사용한다.

Entity를 직접 JSON으로 반환하지 않고 DTO로 변환하면 DB 필드가 추가되더라도 기존 API 응답에 의도하지 않은 값이 노출되지 않는다.

### FurnitureService 작성

다음 파일을 만든다.

```text
src/main/java/com/zimpic/backend/service/FurnitureService.java
```

```java
package com.zimpic.backend.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.zimpic.backend.dto.response.FurnitureResponse;
import com.zimpic.backend.entity.type.FurnitureCategory;
import com.zimpic.backend.repository.FurnitureRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FurnitureService {
    private final FurnitureRepository furnitureRepository;

    @Transactional(readOnly = true)
    public List<FurnitureResponse> findAll() {
        return furnitureRepository
            .findAllByCategoryNotOrderByIdAsc(FurnitureCategory.ETC)
            .stream()
            .map(FurnitureResponse::entityToDto)
            .toList();
    }
}
```

`readOnly = true`는 조회 전용 트랜잭션임을 명시한다. DTO 변환도 트랜잭션 범위 안에서 끝낸다.

### FurnitureController 작성

다음 파일을 만든다.

```text
src/main/java/com/zimpic/backend/controller/FurnitureController.java
```

```java
package com.zimpic.backend.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.zimpic.backend.dto.response.FurnitureResponse;
import com.zimpic.backend.service.FurnitureService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/policy/furniture")
@RequiredArgsConstructor
public class FurnitureController {
    private final FurnitureService furnitureService;

    @GetMapping
    public List<FurnitureResponse> findAll() {
        return furnitureService.findAll();
    }
}
```

`@GetMapping`은 `@RequestMapping`에 지정한 `/api/policy/furniture`의 GET 요청을 이 메서드에 연결한다. Django와 달리 이 가이드에서는 끝에 `/`를 붙이지 않는 주소를 기준으로 사용한다.

### 브라우저에서 Furniture API 확인

이 단계에서는 별도의 `MockMvc` 테스트 파일을 만들지 않고, Spring Boot를 실제로 실행한 뒤 브라우저와 PowerShell에서 응답을 확인한다. 12번의 Seed 데이터 적재와 PostgreSQL 실행이 완료된 상태에서 진행한다.

Spring Boot를 실행한다.

```powershell
$env:DB_PASSWORD="로컬에서_설정한_비밀번호"
.\gradlew.bat bootRun
```

브라우저에서 다음 주소에 접속한다.

```text
http://127.0.0.1:8080/api/policy/furniture
```

`https://`가 아니라 `http://`로 접속해야 한다. 현재 로컬 Spring Boot 서버에는 HTTPS가 설정되어 있지 않다.

다른 PowerShell 창에서 HTTP 상태와 원본 JSON을 함께 확인할 수도 있다.

```powershell
curl.exe -i http://127.0.0.1:8080/api/policy/furniture
```

PowerShell 객체 형태로 응답을 확인하려면 다음 명령을 사용한다.

```powershell
$furnitureList = Invoke-RestMethod http://127.0.0.1:8080/api/policy/furniture
$furnitureList | Select-Object -First 3 | Format-List
```

Swagger UI에서도 확인할 수 있다.

```text
http://127.0.0.1:8080/swagger-ui.html
```

완료 기준은 다음과 같다.

- HTTP 상태가 `200 OK`다.
- 최상위 JSON이 배열이다.
- `ETC` 카테고리의 이사 박스는 목록에서 제외된다.
- 목록이 `id` 오름차순으로 정렬된다.
- JSON 필드명이 `nameEn`, `widthCm`, `needsDisassembly`와 같은 `camelCase`다.
- 제외하기로 한 내부 정책 필드가 응답에 포함되지 않는다.
- 브라우저와 PowerShell의 실제 HTTP 호출이 모두 성공한다.

## 14. FastAPI 추론 API 호출

### 선행 작업: FastAPI 연결 확인

추론 API를 구현하기 전에 Spring Boot에서 FastAPI로 기본 HTTP 요청을 보낼 수 있는지 Health API로 먼저 확인한다. Spring Boot에서 FastAPI를 호출할 때는 Spring `RestClient`를 사용한다.

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

FastAPI의 내부 예외나 응답 전체를 사용자에게 그대로 노출하지 않는다. Health 호출이 성공하면 같은 기본 주소와 `RestClient` 설정을 사용해 추론 API 호출을 구현한다.

### 추론 API 구현

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

## 15. Vision 기능 이전

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

## 16. 견적 기능 이전

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

## 17. 테스트

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

## 18. 작업 체크포인트

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

## 19. 완료 기준

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

## 20. 추가로 결정할 사항

기존 Django에는 정책을 수정할 수 있는 Admin 기능이 있지만 현재 로드맵에는 Spring Boot 전환 이후의 관리자 기능이 정의되어 있지 않다.

초기 Spring Boot 이전에서는 정책 조회와 개발용 Seed SQL 적재까지만 구현한다. Django를 제거하기 전에는 별도 관리자 화면, 제한된 관리 API 또는 다른 운영 방식을 결정해야 한다.

## 21. 배포 전 Flyway 전환

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
