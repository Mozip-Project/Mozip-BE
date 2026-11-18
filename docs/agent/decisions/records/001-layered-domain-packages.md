# 도메인별 계층 패키지로 나누고 헥사고날은 쓰지 않는다

- 상태: accepted
- 기록일: 2026-10-28
- 관련: #3

## 맥락

첫 도메인 코드를 넣기 전에 패키지 구조를 정해야 했다. 백엔드 2인이 MVP를 만드는 상황이고 도메인이 10개 가까이 된다.

## 결정

- `com.mozip.domain.{도메인}` 아래 `controller`, `service`, `repository`, `entity`, `dto`, `exception`으로 나눈다
- 모든 도메인이 같이 쓰는 코드만 `com.mozip.global`
- 헥사고날, 포트/어댑터, Command/Result DTO 분리, CQRS 패키지는 쓰지 않는다

## 근거

- 두 명이 각자 도메인을 맡으므로 도메인 폴더 하나가 한 사람의 작업 단위가 된다. 리뷰와 충돌 범위가 도메인 폴더 안에 갇힌다
- 계층 이름이 Spring 관례와 같아서 설명 없이 읽힌다

## 고려한 대안

- 계층 우선(`controller/`, `service/` 아래 도메인): 도메인이 늘면 한 기능이 여섯 폴더에 흩어진다
- 헥사고날: 포트와 어댑터 파일이 두 배로 늘고, 지금 규모에서는 바꿔 끼울 어댑터가 없다

## 영향 범위

- `docs/agent/rules/code.md` 패키지 절
- `global/health`가 이 구조의 작은 예시

## 변경 기록

- 2026-10-28: 최초 결정
- 2026-11-18: `domain` 패키지를 엔티티 쪽 `entity`로 통일하고 `exception` 패키지 추가. 결정 006이 받아들여지면 `controller`, `service`, `dto`는 api 모듈로 이동
