# 개편 목적과 범위

## 목적

현재 Django 백엔드에 함께 들어 있는 일반 API, 견적 계산, DB 처리, YOLO 추론을 역할에 따라 분리한다.

- 사용자 요청, DB 저장, 가구 매핑, 견적 계산은 Spring Boot가 담당한다.
- Python은 FastAPI를 통해 YOLO 추론 기능만 제공한다.
- 데이터베이스는 PostgreSQL로 변경한다.
- 각 서비스를 Docker로 구성해 AWS EC2에 배포한다.

구조를 직접 설계하고 구현하면서 Spring Boot, 서비스 간 API 통신, Docker와 AWS 배포 과정을 학습하는 것도 이번 개편의 목적이다.

## 기술 변경사항과 선택 이유

기술은 학습 자체보다 실제 서비스에서 맡을 역할과 운영상의 이점을 기준으로 선택한다.

| 기존 | 변경 | 선택 이유 |
| --- | --- | --- |
| Django 단일 백엔드 | Spring Boot와 FastAPI로 분리 | 일반 서버 기능과 AI 기능의 역할을 나눈다. 사용자가 늘어나면 요청이 많은 서버만 여러 대로 늘리거나 이중화하기 쉽고, 각 서버를 독립적으로 수정하고 배포할 수 있다. |
| Django 내부의 YOLO 코드 | FastAPI + YOLO | FastAPI는 화면이나 DB 기능보다 API 작성에 집중된 Python 프레임워크이므로 YOLO 추론만 제공하는 서버에 적합하다. 기존 Python 모델 코드를 유지하면서 별도 API로 제공할 수 있고, Pydantic으로 입출력 값을 검증하며 Swagger 문서를 자동으로 생성한다. 모델이나 Python 라이브러리를 바꿔도 Spring Boot에 주는 영향을 줄일 수 있고 추론 서버만 GPU 서버로 옮기거나 추가할 수 있다. |
| Django의 API·DB·견적 기능 | Spring Boot | 사용자 요청, DB 저장, 가구 매핑, 견적 계산을 담당한다. AI 추론과 분리해 일반 백엔드 기능을 한곳에서 관리한다. |
| MySQL | PostgreSQL | 두 DB 모두 현재 요구사항을 충족하지만, PostgreSQL과 `psql`을 사용하면서 관계형 데이터베이스의 제약조건, 트랜잭션, 운영 방법을 함께 경험하기 위해 선택한다. |

## 구조 분리로 얻는 이점

- Spring Boot는 사용자 API, DB 저장, 견적 계산을 담당하고 FastAPI는 사진 분석만 담당하므로 역할이 명확해진다.
- YOLO 모델을 수정하거나 Python 라이브러리를 변경해도 일반 백엔드에 주는 영향을 줄일 수 있다.
- FastAPI의 입출력 검증과 자동 API 문서를 이용해 Spring Boot와 주고받는 요청·응답 형식을 명확하게 확인할 수 있다.
- 사용자가 늘어나면 Spring Boot와 FastAPI 중 요청이 몰리는 서버만 선택해서 여러 대로 늘릴 수 있다.
- 같은 기능의 서버를 두 대 이상 실행하면 한 서버에 문제가 생겼을 때 다른 서버가 요청을 처리하도록 이중화할 수 있다.
- AI 연산에 더 높은 사양이 필요해지면 FastAPI만 GPU 서버로 옮길 수 있다.
- YOLO 서버에 문제가 발생해도 정책 조회나 기존 견적 조회 같은 Spring Boot 기능은 계속 제공할 수 있다.

다만 서버가 나뉘면 서버 간 API 호출, 타임아웃, 오류 처리처럼 추가로 관리할 부분도 생긴다. 현재 규모에서는 Django 하나로도 충분하지만, 사용자가 늘어날 때 기능별 확장과 이중화가 쉬운 구조를 만들기 위해 분리한다.

## 전체 구성

```text
사용자 브라우저
      │
      ▼
    Nginx
      ├── React 정적 파일 제공
      └── /api 요청 전달
                 │
                 ▼
            Spring Boot
              ├── PostgreSQL
              ├── Kakao API
              └── FastAPI + YOLO
```

EC2에서는 다음 컨테이너를 Docker Compose로 함께 실행한다.

```text
nginx
spring
fastapi
postgres
```

## 기술별 담당 기능 및 기준 버전

목표 구조에서 사용자의 브라우저는 Spring Boot API만 호출한다. Spring Boot가 내부적으로 FastAPI를 호출하며, FastAPI는 YOLO 추론 결과만 반환한다.

| 구분 | 기술 및 기준 버전 | 담당 기능 |
| --- | --- | --- |
| 프론트엔드 | React · 기존 프로젝트 버전 유지 | 이사 유형·평수·공간 정보 입력, 사진 선택 및 업로드, AI 탐지 결과와 bbox 표시, 사용자의 가구 확인·수정, 주소와 상세 조건 입력, 견적 결과와 3D 적재 화면 표시 |
| 백엔드 | Spring Boot 4.1.1 · Java 21 | React 요청을 받는 API 제공, 업로드 이미지 처리, FastAPI 호출, `class_id`와 DB 가구 정보 매핑, 이미지·탐지 결과·견적 저장, 가구 크기·트럭 조합·거리·각종 비용 계산, 최종 결과 응답 |
| AI 추론 서버 | FastAPI · Python 3.11 · YOLO | Spring Boot가 전달한 이미지 수신, 요청·응답 데이터 검증, YOLO 객체 탐지 실행, `class_id`·클래스명·신뢰도·bbox 반환. 라이브러리의 정확한 버전은 `requirements.txt`를 기준으로 한다. |
| 데이터베이스 | PostgreSQL 17.x | 사용자 입력, 이미지 정보, 가구 기준 정보, 탐지 결과, 요금 정책, 견적 결과 등 영속 데이터 저장 |

FastAPI와 YOLO는 사진 속 객체를 탐지할 뿐이며, 가구의 한글 이름과 기본 크기 조회, 견적 계산은 하지 않는다. 이 업무는 Spring Boot가 PostgreSQL의 데이터와 정책을 이용해 처리한다.

## 전체 사용자 흐름

```text
1. 이사 기본 정보와 공간 사진 입력
   사용자 → React → Spring Boot

2. 사진 분석 요청
   Spring Boot → FastAPI → YOLO

3. 순수 탐지 결과 반환
   YOLO → FastAPI → Spring Boot
   반환값: class_id, class_name, confidence, bbox

4. 가구 정보 결합 및 분석 결과 응답
   Spring Boot → PostgreSQL에서 class_id에 해당하는 가구 조회
   Spring Boot → 이미지와 탐지 결과 저장
   Spring Boot → React에 가구명, 기본 크기, bbox 등을 응답

5. 분석 결과 확인 및 상세 정보 입력
   React → 탐지된 가구와 bbox 표시
   사용자 → 가구 포함 여부·크기·추가 가구·주소·층수 등을 확인 또는 수정
   React → Spring Boot에 확정된 견적 조건 전송

6. 견적 산출
   Spring Boot → PostgreSQL의 가구·차량·요금 정책 조회
   Spring Boot → 필요하면 외부 API로 거리 조회
   Spring Boot → 포장 크기, 트럭 조합, 기본·거리·계단·사다리차·특수 가구 비용 계산
   Spring Boot → 견적과 적재 결과 저장 후 React에 응답

7. 결과 표시
   React → 예상 견적, 세부 비용, 추천 차량, 3D 적재 화면 표시
```

핵심 호출 관계를 줄이면 다음과 같다.

```text
사용자
  ↓
React
  ↓ HTTP
Spring Boot ─────→ PostgreSQL
  │
  └── HTTP ─────→ FastAPI ─────→ YOLO
```

Spring Boot가 FastAPI에 사진을 보내는 것은 맞지만, 실제 YOLO 추론을 수행하는 주체는 FastAPI 내부의 YOLO 모델이다. Spring Boot는 전체 업무 흐름을 조정하고 탐지 결과를 비즈니스 데이터와 결합하는 역할을 한다.

## 핵심 원칙

- 사용자는 Spring Boot API만 호출한다.
- Spring Boot가 FastAPI YOLO 서버를 내부에서 호출한다.
- FastAPI YOLO 서버는 DB에 접근하지 않는다.
- 가구 정보 매핑과 견적 계산은 Spring Boot에서 처리한다.
- PostgreSQL, Spring Boot, FastAPI 포트는 EC2 외부에 직접 공개하지 않는다.
- 처음부터 복잡한 운영 구조를 만들기보다 각 구성 요소의 동작을 확인할 수 있는 단순한 구조로 시작한다.

## 배포 구성

| 기술 | 역할과 선택 이유 |
| --- | --- |
| Docker | Spring Boot, FastAPI 등 서비스별 실행 환경을 이미지로 만든다. 로컬에서 확인한 환경을 EC2에서도 동일하게 실행하기 위해 사용한다. |
| Docker Compose | 여러 컨테이너의 실행, 내부 통신, 포트, 환경변수, 데이터 저장 공간을 한 파일에서 관리한다. |
| Nginx | 빌드된 React 화면을 제공하고 외부의 `/api` 요청을 내부 Spring Boot로 전달한다. 외부 요청을 받는 하나의 입구로 사용한다. |
| AWS EC2 | Docker로 구성한 전체 서비스를 실행하고 인터넷에 공개할 서버로 사용한다. |

## 학습 목표

- 기존 Django 코드를 분석해 Spring Boot의 Controller, Service, Repository, Entity 구조로 다시 설계한다.
- Spring Boot와 FastAPI가 HTTP API로 통신하는 방식을 이해한다.
- 각 애플리케이션의 Dockerfile을 작성하고 Docker 이미지를 빌드한다.
- Docker Compose에서 서비스, 네트워크, 포트, 볼륨, 환경변수를 설정한다.
- 호스트에 공개하는 포트와 Docker 내부에서만 사용하는 포트를 구분한다.
- 로컬에서 만든 구성을 AWS EC2에 올리고 실행, 로그 확인, 재시작, 백업까지 경험한다.

