우리 프로젝트에서 모든 공통 의존성 등록의 진입점은 MODULE.bazel이며, 프로젝트 규모 확장에 따른 유지보수 효율성을 위해 다음과 같은 원칙을 따른다.

## 1. 의존성 관리 구조

### 1.1 MODULE.bazel

- 프로젝트 루트의 MODULE.bazel은 외부 모듈 버전 및 의존성을 관리하는 진입점이다.
- Bazel 정책상 MODULE.bazel 파일은 분할이 불가능하므로, 파일이 비대해지는 것을 방지하기 위해 Module Extensions을 활용한다.

### 1.2 Module Extensions

언어별로 관리 효율을 높이기 위해 별도의 .bzl 파일에 정의를 위임하고, MODULE.bazel에서 이를 호출한다.

- Go 의존성: go.mod 파일과 rules_go의 go_deps 확장을 연동하여 관리한다
- Kotlin/Maven 의존성: rulese_jvm_external을 사용하여 Maven 아티팩트를 관리하며, 별도의 .bzl 파일에 아티팩트 리스트를 정의하여 가독성을 확보한다

## 2. 서비스별 디펜던시 참조 및 최적화

### 2.1 불필요한 의존성 전파 방지

전체 의존성 풀에 특정 라이브러리가 등록되어 있더라도, 개별 서비스가 이를 강제로 참조하지 않는다.

### 2.2 서비스별 BUILD.bazel 활용

각 서비스 디렉토리 내의 BUILD.bazel 파일에서 해당 서비스가 실제로 필요한 디펜던시만 deps 항목에 명시한다.