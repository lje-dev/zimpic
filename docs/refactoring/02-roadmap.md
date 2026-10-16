# 구현 및 배포 진행 순서

이번 개편은 백엔드를 먼저 분리하고 로컬에서 기능을 충분히 확인한 다음 Docker를 적용한다. Docker로도 로컬 통합 테스트를 마친 후 같은 구성을 AWS EC2에 배포한다.

```text
기존 Django 백엔드 보존
→ FastAPI 분리 및 로컬 실행
→ Spring Boot 구현 및 로컬 실행
→ React 연결 및 전체 로컬 테스트
→ Flyway로 DB 스키마와 초기 데이터 확정
→ Dockerfile 작성
→ Docker Compose 로컬 통합 테스트
→ AWS EC2 배포
```

예상 프로젝트 구조는 다음과 같다.

```text
zimpic/
├── frontend/          # 기존 React
├── backend/           # 기존 Django, 이전 완료 전까지 비교용으로 유지
├── spring-backend/    # 신규 Spring Boot
├── yolo/              # 신규 FastAPI
└── compose.yaml       # 로컬 기능 검증 후 작성
```

## 1. 기존 Django 백엔드 보존

- 기존 `backend/`는 이전이 끝날 때까지 삭제하거나 수정하지 않는다.
- 기존 정책 CSV와 YOLO 모델 파일을 그대로 유지한다.
- 기능을 옮길 때 필요한 기존 코드와 API 응답을 그때그때 확인한다.
- 새 구현 결과가 다르면 기존 Django를 실행해 동일한 입력으로 비교한다.

완료 기준: 기존 Django 코드와 데이터를 새 구현의 비교 대상으로 사용할 수 있다.

## 2. FastAPI YOLO 서버 분리 및 로컬 실행

기존 Django에서 모델 로딩과 추론 코드를 분리해 FastAPI 서비스로 옮긴다.

상세 진행 방법은 [FastAPI YOLO 서버 분리 가이드](detail/02-fastapi.md)를 참고한다.

1. `/health` API를 만든다.
2. YOLO 모델을 서버 시작 시 한 번만 로딩한다.
3. 이미지 한 장을 받아 탐지 JSON을 반환하는 API를 만든다.
4. 이미지의 Content-Type과 용량을 기본 검증한다.
5. 로컬 Python 환경에서 FastAPI를 실행한다.
6. 이미지 업로드와 탐지 결과를 테스트한다.

초기에는 워커 1개로 실행하고 메모리 사용량과 응답 시간을 측정한다. 상용 배포 전에는 사용 중인 Ultralytics 버전과 모델의 라이선스 조건도 확인한다.

실제 이미지 형식과 픽셀 크기 검증을 포함한 보완 작업은 [배포 전·후 추가 고려사항](03-production-checklist.md)에 기록한다.

완료 기준: Docker 없이 실행한 FastAPI가 이미지에 대한 정상적인 탐지 JSON을 반환한다.

## 3. PostgreSQL 로컬 환경 준비

Docker를 적용하기 전에는 로컬에 설치한 PostgreSQL을 사용할 수 있다.

상세 진행 방법은 [PostgreSQL 로컬 환경 준비 가이드](detail/03-postgresql.md)를 참고한다.

1. PostgreSQL을 설치하고 실행한다.
2. `zimpic` DB와 Spring Boot에서 사용할 사용자를 생성한다.
3. `psql`로 접속해 DB 연결을 확인한다.
4. Spring Boot에 입력할 DB 주소, 사용자명과 비밀번호를 확인한다.

이 단계에서는 테이블을 직접 만들거나 데이터를 추가하지 않는다. 테이블은 4번에서 JPA Entity를 작성한 뒤 Spring Boot가 자동으로 생성하도록 한다.

완료 기준: 로컬 PostgreSQL의 `zimpic` DB에 지정한 사용자로 접속할 수 있다.

## 4. Spring Boot 구현 및 로컬 실행

Spring Boot는 처음에 최소 연결을 확인한 뒤 기존 기능을 단계적으로 옮긴다.

상세 진행 방법은 [Spring Boot 백엔드 준비 및 단계별 구현 가이드](detail/04-spring-boot.md)를 참고한다.

### 기본 연결

1. Java 21과 Spring Boot 4.1.1 프로젝트를 만든다.
2. PostgreSQL 연결 정보를 설정한다.
3. JPA Entity를 작성하고 개발 환경의 `ddl-auto`를 `update`로 설정해 테이블을 자동 생성한다.
4. Repository를 작성하고 데이터 저장과 조회를 확인한다.
5. JPA가 테이블을 생성한 뒤 정책 초기 데이터 SQL을 한 번 실행하고 조회를 확인한다.
6. `/health` API를 만든다.
7. 로컬 FastAPI의 `/health`와 추론 API를 호출한다.
8. FastAPI 호출에 연결 및 응답 타임아웃과 오류 처리를 적용한다.

개발 중에는 JPA Entity 변경을 테이블에 반영하기 위해 `ddl-auto=update`를 사용한다. 전체 로컬 테스트가 끝나면 6번에서 Flyway로 테이블과 초기 데이터 생성 과정을 확정한다.

구현부는 역할에 따라 다음과 같이 분리한다.

- Entity: PostgreSQL 테이블과 연결되는 객체
- Repository: 데이터 저장 및 조회
- Service: 견적 계산과 같은 비즈니스 로직
- Controller: React 요청을 받고 응답 반환
- DTO: API 요청 및 응답 형식
- FastAPI Client: Spring Boot에서 FastAPI 호출

Docker를 사용하지 않는 이 단계의 연결 주소는 다음과 같다.

```text
FastAPI:    http://localhost:8000
PostgreSQL: jdbc:postgresql://localhost:5432/zimpic
```

### 기존 Django 기능 이전

1. 정책 데이터와 가구 정보
2. 이미지 정보와 탐지 결과 저장
3. YOLO 클래스와 가구 매핑
4. 가구 크기와 포장 크기 계산
5. 트럭 조합 계산
6. Kakao API 거리 조회
7. 기본·거리·계단·사다리차·특수가구 비용 계산
8. 견적 결과 저장 및 응답

기능 하나를 옮길 때마다 기존 Django 결과와 비교한다. 차이가 생기면 다음 기능으로 넘어가기 전에 원인을 확인한다.

완료 기준: Spring Boot가 로컬 FastAPI와 PostgreSQL을 사용하고, 대표 테스트에서 기존 Django와 핵심 결과가 일치한다.

## 5. React 연결 및 전체 로컬 테스트

1. React의 기존 Django API 연결을 Spring Boot API로 변경한다.
2. React, Spring Boot, FastAPI, PostgreSQL을 각각 로컬에서 실행한다.
3. 사진 업로드와 AI 탐지 결과를 확인한다.
4. 사용자 수정 사항과 주소 정보를 입력한다.
5. 트럭 구성과 견적 결과를 확인한다.

완료 기준: Docker 없이 로컬에서 전체 사용자 흐름이 정상 동작한다.

## 6. Flyway로 DB 스키마와 초기 데이터 확정

로컬 기능 개발 중에는 JPA가 테이블을 자동으로 변경하지만, Docker와 운영 환경에서는 빈 DB를 항상 같은 구조와 초기 데이터로 준비할 수 있어야 한다.

DB 스키마 관리 방식은 이 단계부터 다음과 같이 전환한다.

- 개발 중: Entity 수정 후 JPA의 `ddl-auto=update`로 로컬 DB에 자동 반영
- 최초 배포 준비: Flyway를 도입하고 `ddl-auto=validate`로 변경
- 운영 이후: Entity 수정과 Flyway 마이그레이션 SQL 추가를 함께 진행
- 일반 데이터: 테이블 DDL이 아니라 기존 API와 JPA를 통해 조회·수정

1. 최종 JPA Entity와 PostgreSQL 테이블 구조를 비교한다.
2. Spring Boot에 Flyway 의존성을 추가한다.
3. 전체 테이블 생성 SQL을 `V1__create_tables.sql`로 작성한다.
4. 정책 초기 데이터 SQL을 `V2__insert_policy_data.sql`로 작성한다.
5. `ddl-auto`를 `update`에서 `validate`로 변경한다.
6. 로컬 `zimpic` DB를 비우고 다시 생성한다.
7. Spring Boot를 실행해 Flyway가 V1과 V2를 순서대로 실행하는지 확인한다.
8. `flyway_schema_history`에서 실행 이력을 확인한다.
9. Spring Boot를 재실행해 이미 적용된 SQL이 중복 실행되지 않는지 확인한다.
10. 새로 만든 DB에서 전체 사용자 흐름을 다시 테스트한다.

Flyway SQL은 Spring Boot 리소스에 포함한다.

```text
spring-backend/src/main/resources/db/migration/
├── V1__create_tables.sql
└── V2__insert_policy_data.sql
```

완료 기준: 빈 PostgreSQL에서 Spring Boot를 실행하면 Flyway가 테이블과 정책 데이터를 준비하고, JPA 검증과 전체 API 테스트가 통과한다.

## 7. 서비스별 Dockerfile 작성 및 실행

로컬 기능 검증이 끝난 다음 각 애플리케이션을 이미지로 만든다.

- `Dockerfile`은 서비스 하나를 Docker 이미지로 만드는 방법을 정의한다.
- FastAPI용 Dockerfile을 작성하고 이미지 빌드와 컨테이너 실행을 확인한다.
- Spring Boot용 Dockerfile을 작성하고 이미지 빌드와 컨테이너 실행을 확인한다.
- React를 빌드하고 Nginx가 제공하도록 Nginx용 Dockerfile을 작성한다.
- PostgreSQL은 직접 이미지를 만들지 않고 공식 이미지를 사용한다.

각 컨테이너를 개별 실행한 뒤 로컬 실행 때 사용했던 API 테스트를 동일하게 반복한다.

완료 기준: FastAPI, Spring Boot, Nginx 컨테이너가 각각 로컬에서 정상 실행된다.

## 8. Docker Compose 로컬 통합 테스트

`compose.yaml`은 여러 컨테이너의 포트, 네트워크, 환경변수, 볼륨과 실행 방법을 한 파일에 정의한다.

다음 서비스를 함께 실행한다.

```text
nginx
spring-api
yolo-inference
postgres
```

Docker Compose 안에서는 `localhost` 대신 서비스 이름을 사용한다.

```text
FastAPI:    http://yolo-inference:8000
PostgreSQL: jdbc:postgresql://postgres:5432/zimpic
```

통합 테스트 항목은 다음과 같다.

- `docker compose up --build`로 전체 서비스를 실행한다.
- React부터 견적 결과까지 전체 흐름을 다시 테스트한다.
- 환경변수로 비밀번호와 API 키를 전달한다.
- PostgreSQL 및 이미지 저장 경로에 volume을 연결한다.
- 컨테이너를 재생성해도 DB와 이미지가 유지되는지 확인한다.
- 각 서비스의 로그와 healthcheck를 확인한다.
- FastAPI 장애와 타임아웃 상황에서 Spring Boot 응답을 확인한다.
- 개발용 공개 포트와 운영용 공개 포트를 구분한다.

완료 기준: Docker Compose 하나로 전체 서비스가 로컬에서 정상 동작하고 데이터가 유지된다.

## 9. AWS EC2 배포

로컬에서 검증한 Docker 구성을 EC2로 옮긴다.

1. 로컬 측정 결과를 바탕으로 EC2 사양과 CPU/GPU 사용 여부를 결정한다.
2. 소스 또는 빌드한 이미지를 EC2에 전달한다.
3. 운영 환경변수와 volume 경로를 설정한다.
4. Docker Compose로 전체 서비스를 실행한다.
5. Nginx의 HTTP/HTTPS만 외부에 공개한다.
6. 도메인과 HTTPS를 설정한다.
7. 컨테이너 로그, 재시작 정책, 메모리 사용량을 확인한다.
8. PostgreSQL과 업로드 이미지의 백업 방법을 확인한다.
9. 외부에서 사진 분석부터 견적 결과까지 최종 테스트한다.

완료 기준: EC2 재부팅 이후에도 서비스가 다시 실행되고 데이터가 유지되며, 외부에서는 Nginx를 통해서만 접근할 수 있다.

## 10. 운영 및 DB 변경 관리

최초 배포가 끝난 뒤에는 애플리케이션 코드와 PostgreSQL 스키마의 변경 이력을 함께 관리한다.

### DB 스키마 관리 원칙

- 개발 중에는 `ddl-auto=update`를 사용한다.
- 최초 배포 준비 단계에서 Flyway를 도입한다.
- 운영 환경에서는 `ddl-auto=validate`를 사용한다.
- 운영 이후에는 Entity와 Flyway 마이그레이션 SQL을 함께 변경한다.
- 운영 DB의 스키마를 임의로 직접 변경하지 않는다.
- 견적과 사용자 입력 같은 일반 데이터는 API와 JPA를 통해 조회·수정한다.

### 무중단 배포를 위한 DB 변경 원칙

- 구버전과 신버전 애플리케이션이 동시에 사용할 수 있도록 DB 변경의 하위 호환성을 유지한다.
- 새 컬럼 추가, 애플리케이션 코드 전환, 기존 컬럼 제거를 서로 다른 배포 단계로 나눈다.
- 기존 컬럼의 삭제나 이름 변경을 신버전 배포와 동시에 처리하지 않는다.
- 오래 걸리는 DDL과 대량 데이터 변경은 애플리케이션 배포와 분리한다.
- 모든 서버가 신버전으로 교체된 것을 확인한 뒤 더 이상 사용하지 않는 컬럼과 제약조건을 제거한다.

완료 기준: 운영 DB의 모든 스키마 변경이 Flyway 이력에 기록되고, 배포 중 구버전과 신버전이 동시에 실행되어도 DB 오류가 발생하지 않는다.

