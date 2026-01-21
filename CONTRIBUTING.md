# Contributing Guidelines


When contributing to this repository, please first discuss the change you wish to make via issue, email, or any other method with the owners of this repository before making a change.

Please note we have a code of conduct, please follow it in all your interactions with the project.

---
## 1. Issue
새로운 제안이나 버그 발견 시 이슈를 먼저 생성해주세요.

**Issue 탬플릿 사용:**
`.github/ISSUE_TEMPLATE`하위 템플릿과 연계하여 작성해주세요.

| Issue Type    | About                                     |
|---------------|-------------------------------------------|
| bug           | Internal bug report                       |
| chore         | Maintenance or housekeeping task          |
| documentation | Documentation update or request           |
| enhancement   | Improvement to an existing feature        |
| feature       | New feature request                       |
| incident      | Production incident / service outage      |
| postmortem    | Incident postmortem / root cause analysis |
| question      | Internal technical question               |

### **상세 기록**
Summary를 작성하고, 이슈 유형에 맞게 필요한 내용을 추가로 작성해주세요.
- bug의 경우 재현 방법(Steps to Reproduce)과 기대 결과(Expected Result), 실제 결과(Actual Result)를 상세히 작성해주세요.

---
## 2. Branch Strategy (Issue-based Workflow)
모든 브랜치는 생성된 이슈를 기반으로 생성하며, `develop` 브랜치에서 분기합니다.

**브랜치 생성 규칙**: `prefix/(이슈 번호)-Summary`

prefix는 Issue Type과 같습니다.

---

## 3. Commit Convention
커밋 메시지는 다음과 같은 형식을 사용합니다:
`Type(Scope): Summary (Issue No)`

| Type            | About                             |
|-----------------|-----------------------------------|
| **feat**        | 새로운 기능 추가                         |
| **fix**         | 버그 수정                             |
| **enhancement** | 기존 기능의 향상 및 개선                    |
| **docs**        | 문서 수정 (README, 가이드 등)             |
| **chore**       | 빌드 업무 수정, 패키지 매니저 설정 등 (코드 수정 없음) |
| **hotfix**      | 긴급한 버그 수정                         |
| **test**        | 테스트 코드 추가 및 수정                    |
| **refactor**    | 코드 리팩토링 (기능 변화 없음)                |

| Scope         | About            |
|---------------|------------------|
| contracts     | 컨트랙트 관련 작업       |
| documents     | 일반 문서 관련 작업      |
| packages      | 패키지 및 모듈 관련 작업   |
| admission     | 입학 서비스 관련 작업     |
| analytics     | 분석 서비스 관련 작업     |
| application   | 지원서 서비스 관련 작업    |
| document      | 특정 문서 파일 관련 작업   |
| evaluation    | 평가 서비스 관련 작업     |
| gateway       | 게이트웨이 관련 작업      |
| identity      | 인증 및 신원 관리 관련 작업 |
| notification  | 알림 서비스 관련 작업     |
| observability | 모니터링 및 관측성 관련 작업 |
| schedule      | 스케줄 서비스 관련 작업    |
| infra         | 인프라 관련 작업        |
| ci            | CI/CD 파이프라인 관련 작업 |
---

## 4. Pull Request Process
* 다음과 같은 형식으로 작성합니다: `Type(Scope): Summary (이슈 번호)`

* 멀티 템플릿의 경우, PR 생성했을 때의 URL 뒤에 &template={템플릿 파일 이름} 을 붙여줘야 합니다.

* **PR 템플릿 사용:**
`.github/PULL_REQUEST_TEMPLATE.md`하위 템플릿과 연계하여 작성해주세요.

* **이슈 연결:** PR 설명란에 `Closes #이슈번호`를 적어 해당 이슈가 자동으로 닫히도록 설정합니다.

* **리뷰 프로세스:** 
  1. 하나 이상의 승인(Approve)이 필요합니다.
  2. 리뷰어의 수정 요청이 있다면 반영 후 다시 알립니다.
  3. 승인 완료 후 머지(Merge)를 진행합니다.

* **PR 시 주의사항**
  * 발생한 Conflict에 대해서는 PR 작성자가 해결합니다.
  * 가능 한 작은 크기의 PR을 제출하여 리뷰어가 쉽게 검토하도록 합니다.
  * 너무 큰 PR은 여러개로 분리합니다.
  * PR 작성 시 github 프로젝트를 선택하지 않습니다.
