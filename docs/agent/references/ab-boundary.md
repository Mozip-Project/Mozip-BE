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
| `OrganizationAccessChecker.isOperatorOf(userId, organizationId)` | B | A (공고 게시 등 운영진 권한 검사) | 구현됨 (#36) |
| `OrganizationAccessChecker.isRepresentativeOf(userId, organizationId)` | B | A, B | 구현됨 (#36) |
| `OrganizationAccessChecker.getOrganization(organizationId)` | B | A (없으면 `ORG_404`) | 구현됨 (#36) |
| `SubscriptionService.hasActivePlan(organizationId)` | A | A (`publishPosting`, #65) | 임시 구현 `TemporarySubscriptionService` (#65). 항상 true. 9단계(결제)에서 실제 구현으로 교체 |
| `SubscriptionService.startFreeTrial(organizationId)` | A | B (단체 생성 직후) | 임시 구현 `TemporarySubscriptionService` (#65, 아무 것도 안 함). 9단계(결제)에서 A가 교체 |
| 이용권 검사 (`ORG_402 SUBSCRIPTION_REQUIRED`) | A | B (유료 기능 호출 시) | 구현됨. `publishPosting`에서 사용 (#65) |
| `NotificationService.send(userId, type, organizationId, title, linkUrl)` (messageId를 받는 오버로드 포함) | A | B (`inviteMember`→INVITATION, `assignReviewers`→EVALUATION_REQUEST, 결과 안내→DOC_RESULT/FINAL_RESULT) | 구현됨 (#48) |
| `MailSender` | A | B (결과 안내 발송) | 미정. B 결과 안내 순서 전에 A가 만들거나 B가 임시 구현 |
| `User.contactEmail` | A | B (결과 안내 수신 주소) | 미정. A가 컬럼만 먼저 추가하거나 B가 지원서 `profile_snapshot`에서 임시로 꺼냄 |
| `PostingResultChecker.isDocResultConfirmed(postingId)` | B | A (`advancePostingStatus`의 DOC_REVIEW -> DOC_RESULT_NOTICE 조건, #66) | 임시 구현 `TemporaryPostingResultChecker` (#66). 항상 true. B가 서류 평가 결과 취합을 구현하면 실제 구현으로 교체 |
| `PostingResultChecker.isFinalResultConfirmed(postingId)` | B | A (`advancePostingStatus`의 FINAL_RESULT 전환 조건, #66) | 임시 구현 `TemporaryPostingResultChecker` (#66). 항상 true. B가 최종 결과 확정을 구현하면 실제 구현으로 교체 |
| `PostingService.getPostingStatus(postingId)` | A | B (평가·면접 화면을 열어도 되는 단계인지 확인) | 구현됨 (#66). B가 아직 안 씀 |
| `InterviewAvailabilityService.save(applicationId, availableTimes)` | B | A (`saveApplicationDraft`에서 면접 가능 시간 저장, #69) | 구현됨 (#106). 열려 있는 블록의 시작 시각인지 확인하고 `Application.availableTimes`를 통째로 교체. 이 검증은 `ApplicationService`에서 옮겨 옴. 제출 뒤 재선택은 `reselect`(`selectInterviewTimes`) |
| `ApplicationService.getSubmittedApplications(postingId)` | A | B (서류 평가 대상 조회, #69) | 구현됨 (#69). B가 아직 안 씀 |

## 공유하는 엔티티

| 엔티티 | 소유 | B가 쓰는 부분 | 상태 |
|---|---|---|---|
| `Application` | A | `doc_result`, `final_result` 컬럼과 변경 메서드 `decideDocResult`, `decideFinalResult`. `available_times` 컬럼과 `changeAvailableTimes` | 컬럼 존재 (#69). 변경 메서드는 아직 B가 안 만듦 (지금은 PENDING에서 안 바뀜). `changeAvailableTimes`는 B가 추가함 (#106) |
| `Posting` | A | `doc_result_confirmed_at`, `final_result_confirmed_at`, `timetable_confirmed_at`, 면접 설정 컬럼 5개(`interviewMinutes`, `breakMinutes`, `assignRule`, `maxApplicantsPerInterview`, `maxParallelInterviews`) | 컬럼 추가됨 (#65). `timetable_confirmed_at`은 `InterviewRepository.confirmTimetable`의 조건부 UPDATE로 채우고 수정 모드에서 다시 확정하면 `reconfirmTimetable`로 새로 씀 (#107). 나머지 변경 메서드(`decideDocResult` 등)는 아직 B가 안 만듦. `interviewMethod`(온라인/오프라인 구분)는 안 쓰기로 해서 제거됨 (#99). `assignRule`은 값만 저장하고 타임테이블 생성에서는 안 씀 (#77) |
| `Posting.doc_score_per_question`, `Question.short_title` | A | B가 평가 화면에서 읽음 | 컬럼 추가됨 (#65). `short_title`은 아직 입력 경로 없어 항상 null |

## 아직 결정 안 된 것

- 위 표에서 "제안", "미정"인 항목은 A와 합의되면 "결정"으로 바꾸고 날짜를 적는다
- 운영 DB `ddl-auto`를 `validate`로 바꿀지 (`db/README.md`)
- 결정 006의 "Redis 리스너는 worker"는 비동기 작업 기준이고, 알림 SSE용 Redis 구독(`NotificationRedisListener`, #48)은
  연결이 각 api 파드에 로컬로 있어 api에 둠. worker로 옮길지, 이런 "파드 로컬 상태가 필요한 리스너"를 예외로 문서화할지
  확인 필요
- `postings` 필터의 `fields`(활동 분야)는 기준 데이터(Organization 쪽 분류)가 아직 없어서 인자는 받지만 적용하지 않음 (#65).
  B가 단체 쪽에 분야 데이터를 만들면 같이 연결 필요
- `savePostingDraft`의 `PostingDraftInput`은 필드가 null이면 "그 값 유지"로 해석함 (명시적으로 비우는 것과 구분 안 됨).
  실제로 필드를 null로 비워야 하는 경우가 생기면 다른 방식(예: 필드별 Optional 래핑)으로 바꿔야 함
- `advancePostingStatus`/`deletePosting`은 `publishPosting`과 같은 방식으로 posting에서 organizationId를 꺼내 `isOperatorOf`로
  검사함. "현재 단체" 쿠키(결정 007)는 아직 구현된 곳이 없어서(#66) 안 씀 — 쿠키 메커니즘이 생기면 다시 볼지 확인 필요
- 게시된 공고를 중간 단계에서 바로 ENDED로 종료(조기 취소)할 수 있는지는 #66에서 결정 안 함.
  `advancePostingStatus`는 "정해진 순서로만 전환"만 구현했고, FINAL_RESULT 다음에만 ENDED로 갈 수 있음. 조기 취소가 필요하면 별도 이슈
- 단계 전환 시 알림(SCRAP_OPEN, POSTING_NOTICE 등) 발송은 #66 범위에 안 넣음. 알림 발송은 #48/#67(알림 기능)에서
- #69(지원서) 이슈 원안과 다르게 구현한 부분
  - `profileSnapshot`과 서류/최종 결과는 운영진에게만 보여야 한다고 적혀 있었는데, 지금은 지원자 본인도 그대로 봄.
    별도 viewer 인지 리졸버가 필요해서 이번엔 생략함
  - `docResult`/`finalResult`는 "결과 안내 발송 전에는 지원자에게 안 보여줌"만 구현했고(Posting 상태가 DOC_RESULT_NOTICE/FINAL_RESULT
    이상이어야 실제 값, 그 전엔 PENDING), 운영진이라고 먼저 보여주는 구분은 안 함
  - `getResultOf(applicationId)`를 B 임시 구현(항상 null)으로 두는 대신, 위 Posting 상태 기준 게이팅을 A가 직접 구현함.
    `doc_result`/`final_result`가 이미 A 소유 `Application`의 컬럼이라 B 연결 없이도 구현 가능해서 그렇게 함
  - `profileSnapshot`에 이름/연락처/학교만 담음. 학과·학년·활동 경험은 `User`에 아직 그 필드가 없어서 뺌 (회원 프로필 기능 필요)
  - 지원 자격 검사는 학교 인증 여부만 함. 대학생·휴학생 허용 여부는 `User`에 재학 상태를 나타내는 필드가 없어서 검사 안 함
  - DRAFT 지원서를 지원자가 지우는 기능은 안 만듦 (할 일/API 목록에 없었음)
  - `Attachment.url`은 그냥 문자열로 저장만 함. #34(presigned URL)가 아직 안 머지돼서 실제 S3 발급/검증과는 연동 안 함
