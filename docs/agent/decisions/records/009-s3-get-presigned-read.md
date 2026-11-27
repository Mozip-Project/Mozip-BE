# 업로드 파일 읽기는 조회마다 GET presigned URL 발급, CloudFront 안 씀

- 상태: accepted
- 기록일: 2026-11-27
- 관련: 이슈 #104

## 맥락

S3 버킷이 비공개(Block Public Access 기본값)인데, 업로드 응답의 `fileUrl`을 `CDN_DOMAIN + "/" + fileKey`로 만들어 DB에
저장해 왔다. `CDN_DOMAIN`이 실제로는 S3 버킷 주소라 이 URL로 GET하면 403이 난다. 서버에는 업로드용 PUT presign만 있고
읽기 경로가 없었다.

## 결정

- 버킷은 계속 비공개로 두고, 읽기는 `FileService.presignGet(fileKey)`로 조회 시점마다 GET presigned URL을 만들어 응답한다
- DB에는 전체 URL이 아니라 `fileKey`만 저장한다 (`Organization.logoUrl`, `PostingImage.url`, `Attachment.url`). 필드/컬럼
  이름은 호환을 위해 그대로 두고 의미만 "URL"에서 "fileKey"로 바꿈
- CloudFront 등 CDN은 두지 않는다

## 근거

- presigned URL은 자격 증명으로 로컬에서 서명을 계산할 뿐 S3를 호출하지 않는다. 목록 조회에서 N개를 presign해도 추가
  레이턴시나 S3 비용이 없어, CDN 없이도 성능 문제가 없다
- CDN을 두면 버킷을 공개하거나 CloudFront에 Origin Access Control을 추가로 설정해야 하는데, 지원서 첨부·재학증명서처럼
  권한 확인이 필요한 파일이 섞여 있어 공개 CDN 경로와 맞지 않는다
- fileKey만 저장하면 나중에 CDN을 붙이거나 만료 정책을 바꿔도 DB 마이그레이션이 필요 없다

## 고려한 대안

- CloudFront + OAC로 버킷을 비공개로 둔 채 CDN만 앞에 둔다: 공개 파일(로고, 공고 사진)에는 적합하지만 권한이 필요한
  파일(재학증명서, 지원서 첨부)은 여전히 서버가 매 요청 서명하거나 CloudFront 서명 쿠키/URL을 따로 구현해야 해서,
  두 경로를 다 만드는 비용이 presigned 단일 경로보다 크다고 판단해 버림
- 버킷을 공개로 전환: 지원서 첨부·재학증명서가 비공개여야 해서 버릴 수 없음
- DB에 전체 URL을 그대로 저장하고 읽기 시점에 CDN_DOMAIN을 다른 값으로 교체: 저장된 값이 여전히 "주소"라는 착각을 주고,
  권한 확인이 필요한 필드에 그대로 노출될 위험이 남아 버림

## 영향 범위

- `FileService.presignGet(fileKey)`, `S3Properties.presignedGetExpiration` (기본 10분, PUT과 같음). 공개/비공개 파일을
  다른 만료 시간으로 나누지 않음 — 조회마다 새로 발급되는 값이라 구분해도 얻는 이점이 적어서 단일 값으로 둠
- `PresignedUrlResponse.fileUrl`은 필드 이름은 그대로 두고 의미를 바꿔, CDN 주소 대신 GET presigned URL을 돌려줌.
  `CDN_DOMAIN`/`cdn-domain` 설정은 제거 (`application.yml`, k8s configmap)
- `Organization.logoUrl`, `PostingImage.url`, `Attachment.url`(FILE 타입만) 필드는 GraphQL 리졸버
  (`OrganizationGraphqlController`, `PostingGraphqlController`, `ApplicationGraphqlController`)에서 저장된 fileKey를
  `presignGet`으로 변환해 응답. 컬럼 타입/길이가 그대로라 DDL 추가 없음
- `PostingImageInput.url` → `fileKey`로 이름을 바꾸고 `POSTING_IMAGE` prefix를 검사. `AttachmentInput.url`은 FILE 타입일
  때 `APPLICATION_ATTACHMENT` prefix를 검사 (LINK는 그대로 http(s) 링크)
- `UpdateOrganizationInput.logoUrl`(raw URL을 받던 옛 경로)은 제거. 로고는 `updateOrganizationLogo(fileKey)` 하나로만 바꿈
- `SchoolVerification.certificateUrl`은 이번 범위에서 빠짐. 그 엔티티가 아직 `develop`에 없어서(#76, 별도 브랜치에서
  진행 중) `FilePurpose`에 재학증명서용 값도 추가하지 않았음. #76이 머지된 뒤 같은 패턴(presignGet + 본인/관리자 권한
  확인)으로 이어서 연결해야 함
- `Attachment.url`은 별도 권한 검사를 추가하지 않음. `application`/`myApplications` 쿼리가 이미 지원자 본인 또는 그 공고
  운영진만 조회 가능하게 막아서(`ApplicationService.findDetail`), Attachment까지 내려오는 경로 자체가 이미 그 범위로
  gating되어 있음
- IAM 사용자(`AWS_ACCESS_KEY_ID`)에 `s3:GetObject` 권한 추가는 AWS 콘솔에서 수동으로 해야 함 (이 저장소에는 IAM 정책
  파일이 없음). dev/prod 각각의 버킷 전용 IAM 사용자에 적용
- B 공유: 단체 로고(`Organization.logoUrl`) 저장 값이 전체 URL에서 fileKey로 바뀜. `ab-boundary.md`에 반영

## 변경 기록

- 2026-11-27: 최초 기록
