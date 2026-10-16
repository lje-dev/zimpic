# PostgreSQL 로컬 환경 준비

이 문서는 Spring Boot 구현 전에 로컬 PostgreSQL과 빈 `zimpic` 데이터베이스를 준비하는 과정을 설명한다.

이 단계에서는 테이블을 직접 만들거나 데이터를 추가하지 않는다. 테이블 생성과 초기 데이터 추가는 Spring Boot에서 JPA Entity를 작성할 때 진행한다.

이 프로젝트는 **PostgreSQL 17.11**을 기준으로 한다. PostgreSQL에서 `17`은 메이저 버전이고 `.11`은 버그 및 보안 수정이 반영된 마이너 버전이다.

## 1. PostgreSQL 설치

### Windows

다음 PostgreSQL 공식 Windows 다운로드 페이지에서 EDB 설치 프로그램으로 이동한 뒤 **PostgreSQL 17.11, Windows x86-64**를 내려받아 설치한다.

- [PostgreSQL Windows 다운로드](https://www.postgresql.org/download/windows/)

설치 과정에서 다음 항목을 확인한다.

- 관리자 사용자: `postgres`
- 비밀번호: 설치 중 직접 지정(0000)
- 포트: `5432`
- Command Line Tools 설치

설치 후 PowerShell을 새로 열고 버전을 확인한다.

```powershell
psql --version
```

명령을 찾을 수 없다면 PostgreSQL의 `bin` 디렉터리를 Windows 시스템 환경변수 `Path`에 등록한다.

1. Windows 검색에서 `시스템 환경 변수 편집`을 실행한다.
2. `환경 변수`를 선택한다.
3. `시스템 변수`에서 `Path`를 선택하고 `편집`을 누른다.
4. `새로 만들기`를 누르고 PostgreSQL의 `bin` 디렉터리 경로를 입력한다.

설치 경로 예시는 다음과 같다. 설치한 버전과 위치에 따라 실제 경로는 달라질 수 있다.

```text
C:\Program Files\PostgreSQL\17\bin
```

등록을 마친 후 열려 있던 PowerShell과 VS Code를 모두 종료하고 다시 실행한다. 새 PowerShell에서 버전을 다시 확인한다.

```powershell
psql --version
```

### macOS

다음 공식 macOS 다운로드 페이지에서 PostgreSQL 17용 EDB 설치 프로그램을 받을 수 있다.

- [PostgreSQL macOS 다운로드](https://www.postgresql.org/download/macosx/)

Homebrew가 설치되어 있다면 PostgreSQL 17을 다음 명령으로 설치하고 실행할 수도 있다.

```bash
brew install postgresql@17
brew services start postgresql@17
psql --version
```

기본 포트는 Windows와 동일하게 `5432`를 사용한다.

## 2. PostgreSQL 서버 실행 확인

`psql`은 PostgreSQL 서버에 접속해 SQL을 실행하는 터미널 프로그램이다. `psql --version`이 성공하더라도 PostgreSQL 서버가 실행 중이지 않으면 DB에 접속할 수 없다.

macOS에서 Homebrew로 설치했다면 다음 명령으로 PostgreSQL 서비스 상태를 확인한다.

```bash
brew services list | grep postgresql
```

`postgresql@17`의 상태가 `started`이면 서비스가 실행 중이다. PostgreSQL이 실제로 요청을 받을 수 있는지는 다음 명령으로 확인한다.

```bash
pg_isready -h 127.0.0.1 -p 5432
```

정상적으로 실행 중이면 다음과 같이 `accepting connections`가 표시된다.

```text
127.0.0.1:5432 - accepting connections
```

Homebrew로 설치한 PostgreSQL 서비스는 다음 명령으로 관리한다.

```bash
# 중지
brew services stop postgresql@17

# 시작
brew services start postgresql@17

# 재시작
brew services restart postgresql@17
```

서비스를 중지한 뒤 `pg_isready`를 실행했을 때 `no response`가 표시되는 것은 정상이다.

Windows에서 공식 설치 프로그램을 사용했다면 PostgreSQL 서버는 일반적으로 Windows 서비스로 등록되어 자동 실행된다.

다음 명령으로 접속이 되는지 확인한다.

```powershell
psql -U postgres
```

- `-U postgres`: `postgres` 사용자로 접속
- mac에서는 `psql -U <mac 사용자명> -d postgres`
- 비밀번호: PostgreSQL 설치 중 지정한 관리자 비밀번호
- 비밀번호 입력 중 문자가 화면에 표시되지 않는 것은 정상

접속되면 다음과 같은 프롬프트가 표시된다.

```text
postgres=#
```

## 3. Spring Boot 전용 사용자 생성

Spring Boot가 관리자 사용자인 `postgres`를 직접 사용하지 않도록 애플리케이션 전용 사용자를 만든다.

`postgres=#` 프롬프트에서 다음 SQL을 실행한다.

```sql
CREATE USER zimpic_user WITH PASSWORD '로컬에서_사용할_비밀번호';
```

- `postgres`: PostgreSQL 관리 작업에 사용하는 관리자
- `zimpic_user`: Spring Boot가 DB에 접속할 때 사용할 사용자
- 비밀번호는 실제 사용할 값으로 변경하고 Git에 기록하지 않는다.

성공하면 다음 메시지가 표시된다.

```text
CREATE ROLE
```

## 4. zimpic 데이터베이스 생성

다음 SQL로 `zimpic` 데이터베이스를 생성하고 소유자를 `zimpic_user`로 지정한다.

```sql
CREATE DATABASE zimpic
    OWNER zimpic_user;
```

소유자로 지정하면 이후 Spring Boot가 `zimpic_user`로 접속해 JPA 테이블을 생성하고 변경할 수 있다.

성공하면 다음 메시지가 표시된다.

```text
CREATE DATABASE
```

## 5. zimpic_user 접속 확인

관리자 접속을 종료한다.

```text
\q
```

PowerShell 또는 터미널에서 `zimpic_user`로 `zimpic` 데이터베이스에 접속한다.

```powershell
psql -U zimpic_user -d zimpic -h localhost -p 5432
```

- `-U`: 접속할 사용자
- `-d`: 접속할 데이터베이스
- `-h`: PostgreSQL 서버 주소
- `-p`: PostgreSQL 서버 포트

접속 후 현재 데이터베이스와 사용자를 확인한다.

```sql
SELECT current_database(), current_user;
```

예상 결과는 다음과 같다.

```text
 current_database | current_user
------------------+-------------
 zimpic           | zimpic_user
```

현재 테이블 목록도 확인할 수 있다.

```text
\dt
```

아직 Spring Boot JPA를 실행하지 않았으므로 테이블이 없어도 정상이다.

## 6. 자주 사용하는 psql 명령

```text
\l         데이터베이스 목록
\du        사용자 목록
\dt        현재 데이터베이스의 테이블 목록
\conninfo  현재 접속 정보
\q         psql 종료
```

위 명령은 SQL이 아니라 `psql` 전용 명령이므로 끝에 세미콜론을 붙이지 않는다. `CREATE USER`, `SELECT`와 같은 SQL 문에는 세미콜론을 붙인다.

## 7. Spring Boot에서 사용할 접속 정보

다음 단계에서 Spring Boot에 입력할 값은 아래와 같다.

```text
DB 주소: jdbc:postgresql://localhost:5432/zimpic
사용자: zimpic_user
비밀번호: 사용자 생성 시 지정한 로컬 비밀번호
```

DB 비밀번호는 Git에 올라가는 문서나 공용 설정 파일에 직접 작성하지 않는다. Spring Boot 구현 단계에서 환경변수 또는 Git에서 제외한 로컬 설정으로 관리한다.

## 8. 완료 확인

- PostgreSQL이 설치되어 있다.
- PostgreSQL 서버가 실행 중이다.
- `zimpic_user` 사용자가 생성되어 있다.
- `zimpic` 데이터베이스가 생성되어 있다.
- `zimpic_user`로 `zimpic` 데이터베이스에 접속할 수 있다.
- 테이블과 초기 데이터는 아직 생성하지 않는다.

여기까지 확인되면 PostgreSQL 로컬 환경 준비가 완료된 것이다. 다음 단계에서 Spring Boot와 PostgreSQL을 연결하고 JPA Entity를 이용해 테이블을 생성한다.
