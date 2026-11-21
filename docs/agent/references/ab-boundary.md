# A와 B의 도메인 경계

백엔드는 두 명이 도메인을 나눠 맡는다. 다른 사람 영역의 엔티티나 테이블은 직접 바꾸지 않고, 필요한 기능은 아래 인터페이스로 요청한다. 경계가 바뀌면 이 표를 먼저 고친다.

## 담당 도메인

| 담당 | 도메인 (패키지) | 내용 |
|---|---|---|
| A | `auth`, `user`, `school` | 회원 가입과 로그인, 토큰, 학교 인증, 프로필 |
| A | `posting` | 공고 작성, 게시, 목록과 검색 |
| A | `application` | 지원서 작성, 임시 저장, 제출, 내 지원 내역 |
| A | `payment` | 이용권, 무료 체험, UP 잔여권 |
| A | 파일 업로드, 메일과 알림 발송 | S3 presigned URL, `MailSender`, `NotificationService` |
| A | `global`, `devtools`, 인프라 | 공통 설정, 인증 우회, k8s와 배포 |
| B | `organization` | 단체 생성과 프로필, 운영진(`Member`), 팀, 초대, 대표자 인수 |
| B | `evaluation` | 서류 평가 작성, 취합, 결과 확정 |
| B | `interview` | 면접 시간 선택, 타임테이블 생성과 배정, 면접 평가 |
| B | `message` | 결과 안내 메시지 작성, 발송, 전달 상태 |

- `application`, `posting` 엔티티는 A 소유지만 B가 쓰는 결과 컬럼과 변경 메서드가 있다. 아래 "공유하는 엔티티" 참고
- 양쪽이 다 쓰는 enum이나 공통 코드는 `global`에 두고 PR에서 상대를 리뷰어로 지정한다

## 서로 호출하는 인터페이스

쓰는 쪽이 먼저 필요하면 쓰는 쪽 PR에서 인터페이스와 임시 구현을 넣고, 만드는 쪽이 자기 순서에서 실제 구현으로 바꾼다 (결정 005). 임시 구현에는 `// TODO(A 이용권): ...`처럼 누구 몫인지 적는다. 전부 Security가 아니라 서비스에서 부르는 평범한 Spring 빈이다.

| 인터페이스 | 소유 | 호출 | 상태 |
|---|---|---|---|
| `OrganizationAccessChecker.isOperatorOf(userId, organizationId)` | B | A (공고 게시 등 운영진 권한 검사) | 임시 구현 `TemporaryOrganizationAccessChecker` (#65). `Member` 테이블로만 판단. B가 단체 도메인을 구현하면 실제 구현으로 교체 |
| `OrganizationAccessChecker.isRepresentativeOf(userId, organizationId)` | B | A, B | 아직 안 씀. #65는 isOperatorOf만 필요 |
| `OrganizationAccessChecker.getOrganization(organizationId)` | B | A (없으면 `ORG_404`) | 임시 구현 `TemporaryOrganizationAccessChecker` (#65) |
| `SubscriptionService.hasActivePlan(organizationId)` | A | A (`publishPosting`, #65) | 임시 구현 `TemporarySubscriptionService` (#65). 항상 true. 9단계(결제)에서 실제 구현으로 교체 |
| `SubscriptionService.startFreeTrial(organizationId)` | A | B (단체 생성 직후) | 임시 구현 `TemporarySubscriptionService` (#65, 아무 것도 안 함). 9단계(결제)에서 A가 교체 |
| 이용권 검사 (`ORG_402 SUBSCRIPTION_REQUIRED`) | A | B (유료 기능 호출 시) | 구현됨. `publishPosting`에서 사용 (#65) |
| `MailSender`, `NotificationService` | A | B (결과 안내 발송) | 미정. B 결과 안내 순서 전에 A가 만들거나 B가 임시 구현 |
| `User.contactEmail` | A | B (결과 안내 수신 주소) | 미정. A가 컬럼만 먼저 추가하거나 B가 지원서 `profile_snapshot`에서 임시로 꺼냄 |
| `PostingResultChecker.isDocResultConfirmed(postingId)` | B | A (`advancePostingStatus`의 DOC_REVIEW -> DOC_RESULT_NOTICE 조건, #66) | 임시 구현 `TemporaryPostingResultChecker` (#66). 항상 true. B가 서류 평가 결과 취합을 구현하면 실제 구현으로 교체 |
| `PostingResultChecker.isFinalResultConfirmed(postingId)` | B | A (`advancePostingStatus`의 FINAL_RESULT 전환 조건, #66) | 임시 구현 `TemporaryPostingResultChecker` (#66). 항상 true. B가 최종 결과 확정을 구현하면 실제 구현으로 교체 |
| `PostingService.getPostingStatus(postingId)` | A | B (평가·면접 화면을 열어도 되는 단계인지 확인) | 구현됨 (#66). B가 아직 안 씀 |

## 공유하는 엔티티

| 엔티티 | 소유 | B가 쓰는 부분 | 상태 |
|---|---|---|---|
| `Application` | A | `doc_result`, `final_result` 컬럼과 변경 메서드 `decideDocResult`, `decideFinalResult` | 제안. 메서드 두 개만 B가 만들고 소유 |
| `Posting` | A | `doc_result_confirmed_at`, `final_result_confirmed_at`, `timetable_confirmed_at`, 면접 설정 컬럼 6개(`interviewMethod`, `interviewMinutes`, `breakMinutes`, `assignRule`, `maxApplicantsPerInterview`, `maxParallelInterviews`) | 컬럼 추가됨 (#65). 변경 메서드(`decideDocResult` 등)는 아직 B가 안 만듦 |
| `Posting.doc_score_per_question`, `Question.short_title` | A | B가 평가 화면에서 읽음 | 컬럼 추가됨 (#65). `short_title`은 아직 입력 경로 없어 항상 null |

## 아직 결정 안 된 것

- 위 표에서 "제안", "미정"인 항목은 A와 합의되면 "결정"으로 바꾸고 날짜를 적는다
- 운영 DB `ddl-auto`를 `validate`로 바꿀지 (`db/README.md`)
- `postings` 필터의 `fields`(활동 분야)는 기준 데이터(Organization 쪽 분류)가 아직 없어서 인자는 받지만 적용하지 않음 (#65).
  B가 단체 쪽에 분야 데이터를 만들면 같이 연결 필요
- `savePostingDraft`의 `PostingDraftInput`은 필드가 null이면 "그 값 유지"로 해석함 (명시적으로 비우는 것과 구분 안 됨).
  실제로 필드를 null로 비워야 하는 경우가 생기면 다른 방식(예: 필드별 Optional 래핑)으로 바꿔야 함
- `advancePostingStatus`는 `publishPosting`과 같은 방식으로 posting에서 organizationId를 꺼내 `isOperatorOf`로
  검사함. "현재 단체" 쿠키(결정 007)는 아직 구현된 곳이 없어서(#66) 안 씀 — 쿠키 메커니즘이 생기면 다시 볼지 확인 필요
- 게시된 공고를 중간 단계에서 바로 ENDED로 종료(조기 취소)할 수 있는지는 #66에서 결정 안 함.
  `advancePostingStatus`는 "정해진 순서로만 전환"만 구현했고, FINAL_RESULT 다음에만 ENDED로 갈 수 있음. 조기 취소가 필요하면 별도 이슈
- 단계 전환 시 알림(SCRAP_OPEN, POSTING_NOTICE 등) 발송은 #66 범위에 안 넣음. 알림 발송은 #48/#67(알림 기능)에서
