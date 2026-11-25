# 심사 상태 enum(ReviewStatus)을 organization에서 global로 옮긴다

- 상태: proposed
- 기록일: 2026-11-25
- 관련: #76

## 맥락

`ReviewStatus`(PENDING/APPROVED/REJECTED)는 단체 등록 심사(B, `organization` 도메인)를 위해 만들어졌다. #76(학교 인증)에서 `SchoolVerification.status`도 같은 세 값을 쓰는데, 스키마(`user.graphqls`)가 이미 같은 GraphQL 타입 `ReviewStatus`를 재사용하도록 되어 있었다. B의 패키지(`organization.entity`)에 있는 Java enum을 A(`school`) 쪽에서 그대로 import하면 ab-boundary.md의 "다른 사람 영역의 엔티티나 테이블은 직접 바꾸지 않는다" 경계를 넘는다.

## 결정

- `ReviewStatus`를 `com.mozip.domain.organization.entity`에서 `com.mozip.global.common`으로 옮긴다
- `Organization`, `OrganizationService`, `OrganizationKeywordRepository`와 관련 테스트의 import만 고치고 값과 쓰임은 그대로 둔다

## 근거

- ab-boundary.md: "양쪽이 다 쓰는 enum이나 공통 코드는 global에 두고 PR에서 상대를 리뷰어로 지정한다"
- GraphQL 스키마가 이미 하나의 `ReviewStatus` 타입으로 통일해 둬서, Java 쪼에서도 같은 enum을 공유하는 쪼이 둘로 쪼개는 것보다 자연스러움

## 고려한 대안

- `school.entity`에 똑같은 값(PENDING/APPROVED/REJECTED)을 가진 별도 enum을 새로 만듦: GraphQL 쪼에서는 이름만 같으면 직렬화가 똑같이 동작하지만, 같은 이름의 Java enum이 패키지 둘에 존재해 혼동을 일으키고 ab-boundary.md가 권장하는 방식과도 안 맞음
- `organization.entity`에 그대로 두고 `school` 쪼에서 import: B 영역 엔티티 패키지에 A가 의존하게 되어 경계가 흐려짐

## 영향 범위

- `common/src/main/java/com/mozip/domain/organization/entity/Organization.java`
- `api/src/main/java/com/mozip/domain/organization/service/OrganizationService.java`
- `common/src/main/java/com/mozip/domain/organization/repository/OrganizationKeywordRepository.java`
- `api/src/test/java/com/mozip/domain/organization/service/OrganizationServiceTest.java`
- `common/src/test/java/com/mozip/domain/organization/entity/OrganizationEntityTest.java`
- B 리뷰 필요: `organization` 도메인 파일의 import가 바뀌었으니 B가 확인해야 함

## 변경 기록

- 2026-11-25: #76 작업 중 발견, 기록. B 확인 전이라 proposed
