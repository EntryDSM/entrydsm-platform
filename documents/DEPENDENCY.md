# 모노레포 의존성 관리: One-Version Rule 가이드

## 프로젝트 파일 구조

```
entrydsm-platform/
├── MODULE.bazel                      # 메인 모듈 정의 (진입점)
├── go.MODULE.bazel                   # Go 전용 모듈 설정
├── kotlin.MODULE.bazel               # Kotlin 전용 모듈 설정
├── BUILD.bazel                       # 루트 빌드 파일 (별칭 정의)
├── .bazelrc                          # Bazel 설정 파일
├── .bazelversion                     # Bazel 버전 고정
│
├── go.bzl                            # Go 의존성 Extension
├── kotlin.bzl                        # Kotlin 의존성 Extension
├── maven.bzl                         # Maven 의존성 Extension
├── go.work                           # Go workspace 설정
├── go.work.sum                       # Go workspace 체크섬
│
├── contracts/                        # 서비스 간 계약 정의
│   ├── BUILD.bazel
│   └── proto/
│       ├── BUILD.bazel
│       ├── user.proto
│       ├── application.proto
│       └── admin.proto
│
├── packages/                         # 공통 라이브러리/유틸리티
│   ├── BUILD.bazel
│   ├── common/
│   │   ├── BUILD.bazel
│   │   └── src/main/kotlin/
│   │       ├── utils/
│   │       └── constants/
│   │
│   ├── security/
│   │   ├── BUILD.bazel
│   │   └── src/main/kotlin/
│   │       ├── jwt/
│   │       └── crypto/
│   │
│   └── database/
│       ├── BUILD.bazel
│       └── src/main/kotlin/
│           ├── config/
│           └── migration/
│
└── systems/                          # 개별 마이크로서비스
    ├── user-service/
    │   ├── BUILD.bazel
    │   └── src/
    │       ├── main/kotlin/
    │       └── test/kotlin/
    │
    ├── application-service/
    │   ├── BUILD.bazel
    │   └── src/
    │       ├── main/kotlin/
    │       └── test/kotlin/
    │
    ├── admin-service/
    │   ├── BUILD.bazel
    │   └── src/
    │       ├── main/kotlin/
    │       └── test/kotlin/
    │
    └── api-gateway/
        ├── BUILD.bazel
        ├── main.go
        ├── handlers/
        ├── middleware/
        └── config/
```

## One-Version Rule이란?

**One-Version Rule**은 모노레포 내의 모든 코드가 각 외부 의존성의 **단일 버전**만을 사용해야 한다는 원칙입니다.

### 핵심 이점

- **의존성 충돌 방지**: 같은 라이브러리의 여러 버전이 공존하면서 발생하는 런타임 오류 제거
- **빌드 재현성**: 동일한 소스 코드는 항상 동일한 바이너리를 생성
- **유지보수 효율성**: 보안 패치나 버전 업그레이드를 한 곳에서 일괄 적용
- **테스트 신뢰성**: 모든 서비스가 동일한 의존성 버전으로 테스트되어 통합 문제 조기 발견

## 의존성 관리 흐름

```
1. 의존성 정의
   MODULE.bazel (메인)
   ├── go.MODULE.bazel (Go 전용)
   ├── kotlin.MODULE.bazel (Kotlin 전용)
   └── Extension 파일들
       ├── go.bzl (Go 의존성)
       ├── kotlin.bzl (Kotlin 의존성)
       └── maven.bzl (Maven 의존성)

2. 중앙 등록
   MODULE.bazel의 Extension으로 전역 풀 생성

3. 별칭 생성
   루트 BUILD.bazel에서 사용하기 쉬운 이름 정의

4. 서비스별 선택적 사용
   systems/의 각 BUILD.bazel에서 필요한 의존성만 deps에 선언
```

## 새로운 의존성 추가: 단계별 예시

### 시나리오: Kotlin 서비스에 Redis 클라이언트 추가

#### **단계 1: maven.bzl에 의존성 정의**

```python
# maven.bzl
"""Maven 의존성 중앙 관리"""

# 버전 상수 정의 (One-Version Rule 준수)
KOTLIN_VERSION = "1.9.21"
SPRING_BOOT_VERSION = "3.2.0"
JACKSON_VERSION = "2.16.0"
LETTUCE_VERSION = "6.3.0.RELEASE"

def maven_artifacts():
    """프로젝트 전체에서 사용하는 Maven 아티팩트 목록"""
    return [
        # Kotlin 표준 라이브러리
        f"org.jetbrains.kotlin:kotlin-stdlib:{KOTLIN_VERSION}",
        f"org.jetbrains.kotlin:kotlin-reflect:{KOTLIN_VERSION}",
        
        # Spring Boot (모두 동일 버전!)
        f"org.springframework.boot:spring-boot-starter-web:{SPRING_BOOT_VERSION}",
        f"org.springframework.boot:spring-boot-starter-data-jpa:{SPRING_BOOT_VERSION}",
        
        # JSON 처리 (Jackson - 모두 동일 버전!)
        f"com.fasterxml.jackson.core:jackson-databind:{JACKSON_VERSION}",
        f"com.fasterxml.jackson.module:jackson-module-kotlin:{JACKSON_VERSION}",
        
        # 새로 추가: Redis 클라이언트
        f"io.lettuce:lettuce-core:{LETTUCE_VERSION}",
        
        # 데이터베이스
        "org.postgresql:postgresql:42.7.1",
    ]
```

**핵심**:
- 모든 관련 모듈을 **동일한 버전**으로 등록 (예: Jackson 2.16.0)
- 이것이 One-Version Rule의 실천 사례

#### **단계 2: kotlin.MODULE.bazel에서 Extension 로드**

```python
# kotlin.MODULE.bazel
module(
    name = "kotlin_module",
    version = "1.0.0",
)

# Maven 의존성 확장
maven = use_extension("@rules_jvm_external//:extensions.bzl", "maven")
maven.install(
    artifacts = maven_artifacts(),  # maven.bzl에서 정의한 리스트 사용
    repositories = [
        "https://repo1.maven.org/maven2",
    ],
    fetch_sources = True,
    version_conflict_policy = "pinned",  # One-Version Rule 강제
)
use_repo(maven, "maven")
```

#### **단계 3: 루트 BUILD.bazel에 별칭 정의**

```python
# BUILD.bazel (프로젝트 루트)
package(default_visibility = ["//visibility:public"])

# Kotlin 표준 라이브러리
alias(
    name = "kotlin_stdlib",
    actual = "@maven//:org_jetbrains_kotlin_kotlin_stdlib",
)

# Spring Boot
alias(
    name = "spring_boot_starter_web",
    actual = "@maven//:org_springframework_boot_spring_boot_starter_web",
)

alias(
    name = "spring_boot_starter_data_jpa",
    actual = "@maven//:org_springframework_boot_spring_boot_starter_data_jpa",
)

# Jackson JSON 라이브러리
alias(
    name = "jackson_databind",
    actual = "@maven//:com_fasterxml_jackson_core_jackson_databind",
)

alias(
    name = "jackson_kotlin",
    actual = "@maven//:com_fasterxml_jackson_module_jackson_module_kotlin",
)

# 새로 추가: Redis 별칭
alias(
    name = "lettuce_redis",
    actual = "@maven//:io_lettuce_lettuce_core",
)

# 데이터베이스
alias(
    name = "postgresql",
    actual = "@maven//:org_postgresql_postgresql",
)
```

#### **단계 4: 의존성 동기화**

```bash
# Maven 의존성 다운로드 및 캐시 갱신
bazel sync --configure

# 또는 빌드 시 자동 동기화됨
bazel build //...
```

#### **단계 5: 서비스 BUILD.bazel에서 사용**

```python
# systems/user-service/BUILD.bazel
load("@rules_kotlin//kotlin:jvm.bzl", "kt_jvm_library", "kt_jvm_binary")

kt_jvm_library(
    name = "user_service_lib",
    srcs = glob(["src/main/kotlin/**/*.kt"]),
    deps = [
        "//:kotlin_stdlib",              # 별칭 사용
        "//:spring_boot_starter_web",
        "//:spring_boot_starter_data_jpa",
        "//:jackson_databind",
        "//:jackson_kotlin",
        "//:lettuce_redis",
        "//:postgresql",
        
        # 공통 라이브러리 참조
        "//packages/common",
        "//packages/security",
    ],
    resources = glob(["src/main/resources/**/*"]),
)

kt_jvm_binary(
    name = "user_service",
    main_class = "com.entrydsm.user.UserApplicationKt",
    runtime_deps = [":user_service_lib"],
)
```

**최적화 포인트**:
- `application-service`가 Redis를 사용하지 않는다면 deps에 포함하지 않음
- 각 서비스는 필요한 의존성만 명시적으로 선언 (불필요한 의존성 전파 방지)

#### **단계 6: 실제 코드에서 사용**

```kotlin
// systems/user-service/src/main/kotlin/application/UserCacheService.kt
package com.entrydsm.user.application

import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection
import org.springframework.stereotype.Service

@Service
class UserCacheService {
    private val redisClient = RedisClient.create("redis://localhost:6379")
    private val connection: StatefulRedisConnection<String, String> = 
        redisClient.connect()
    
    fun cacheUser(userId: String, userData: String) {
        connection.sync().set("user:$userId", userData)
    }
    
    fun getCachedUser(userId: String): String? {
        return connection.sync().get("user:$userId")
    }
}
```

### 시나리오 2: Go 서비스에 Prometheus 메트릭 추가

#### **단계 1: go.work 업데이트**

```bash
# systems/api-gateway 디렉토리에서 의존성 추가
cd systems/api-gateway
go get github.com/prometheus/client_golang@v1.18.0
cd ../..
```

업데이트된 `go.work`:
```go
// go.work
go 1.21

use (
    ./systems/api-gateway
)

require (
    github.com/gin-gonic/gin v1.10.0
    github.com/prometheus/client_golang v1.18.0
    google.golang.org/grpc v1.60.0
)
```

#### **단계 2: Bazel Go 의존성 자동 생성**

```bash
# go.work를 읽어 go.bzl 자동 업데이트
bazel run @gazelle//:gazelle -- update-repos \
    -from_file=go.work \
    -to_macro=go.bzl%go_dependencies
```

생성된 `go.bzl` (일부):
```python
# go.bzl
# 이 파일은 자동 생성됨 - 직접 수정 금지!
load("@bazel_gazelle//:deps.bzl", "go_repository")

def go_dependencies():
    go_repository(
        name = "com_github_gin_gonic_gin",
        importpath = "github.com/gin-gonic/gin",
        sum = "h1:...",
        version = "v1.10.0",
    )
    
    go_repository(
        name = "com_github_prometheus_client_golang",
        importpath = "github.com/prometheus/client_golang",
        sum = "h1:...",
        version = "v1.18.0",
    )
    
    go_repository(
        name = "org_golang_google_grpc",
        importpath = "google.golang.org/grpc",
        sum = "h1:...",
        version = "v1.60.0",
    )
```

#### **단계 3: go.MODULE.bazel에 use_repo 추가**

```python
# go.MODULE.bazel
module(
    name = "go_module",
    version = "1.0.0",
)

# Go 의존성 확장
go_deps = use_extension("@gazelle//:extensions.bzl", "go_deps")
go_deps.from_file(go_mod = "//:go.work")

# 필요한 Go 패키지 명시적 선언
use_repo(
    go_deps,
    "com_github_gin_gonic_gin",
    "org_golang_google_grpc",
    "com_github_prometheus_client_golang",
)
```

#### **단계 4: 서비스 BUILD.bazel 업데이트**

```python
# systems/api-gateway/BUILD.bazel
load("@rules_go//go:def.bzl", "go_binary", "go_library")

go_library(
    name = "gateway_lib",
    srcs = glob(["*.go"]) + glob(["**/*.go"]),
    importpath = "github.com/EntryDSM/entrydsm-platform/systems/api-gateway",
    deps = [
        "@com_github_gin_gonic_gin//:gin",
        "@org_golang_google_grpc//:grpc",
        "@com_github_prometheus_client_golang//prometheus",         
        "@com_github_prometheus_client_golang//prometheus/promhttp",
        
        # 계약 참조
        "//contracts/proto:user_proto_go",
        "//contracts/proto:application_proto_go",
    ],
)

go_binary(
    name = "api_gateway",
    embed = [":gateway_lib"],
)
```

#### **단계 5: Go 코드에서 사용**

```go
// systems/api-gateway/main.go
package main

import (
    "github.com/gin-gonic/gin"
    "github.com/prometheus/client_golang/prometheus"
    "github.com/prometheus/client_golang/prometheus/promhttp"
)

var (
    requestCounter = prometheus.NewCounterVec(
        prometheus.CounterOpts{
            Name: "api_requests_total",
            Help: "Total API requests",
        },
        []string{"method", "endpoint"},
    )
)

func init() {
    prometheus.MustRegister(requestCounter)
}

func main() {
    r := gin.Default()
    
    // Prometheus 메트릭 엔드포인트
    r.GET("/metrics", gin.WrapH(promhttp.Handler()))
    
    // 메트릭 수집 미들웨어
    r.Use(func(c *gin.Context) {
        requestCounter.WithLabelValues(c.Request.Method, c.FullPath()).Inc()
        c.Next()
    })
    
    r.Run(":8080")
}
```

## 루트 BUILD.bazel 별칭 시스템 설명

### 별칭 시스템의 목적

별칭(alias)은 복잡한 외부 의존성 경로를 간단하고 일관된 이름으로 참조할 수 있게 해주는 시스템입니다.

### 별칭 사용의 장점

1. **가독성 향상**
   ```python
   # 별칭 없이 사용
   deps = ["@maven//:com_fasterxml_jackson_core_jackson_databind"]
   
   # 별칭 사용
   deps = ["//:jackson_databind"]
   ```

2. **리팩토링 용이성**
    - 버전 변경 시 루트 BUILD.bazel의 별칭만 수정
    - 모든 서비스의 BUILD.bazel은 변경 불필요

3. **일관성 보장**
    - 모든 서비스가 동일한 별칭 사용
    - One-Version Rule 자동 준수

### 전체 별칭 정의 예시

```python
# BUILD.bazel (프로젝트 루트)
package(default_visibility = ["//visibility:public"])

# ============================================
# Kotlin/Maven 의존성 별칭
# ============================================

# Kotlin 표준 라이브러리
alias(
    name = "kotlin_stdlib",
    actual = "@maven//:org_jetbrains_kotlin_kotlin_stdlib",
)

alias(
    name = "kotlin_reflect",
    actual = "@maven//:org_jetbrains_kotlin_kotlin_reflect",
)

# Spring Boot
alias(
    name = "spring_boot_starter_web",
    actual = "@maven//:org_springframework_boot_spring_boot_starter_web",
)

alias(
    name = "spring_boot_starter_data_jpa",
    actual = "@maven//:org_springframework_boot_spring_boot_starter_data_jpa",
)

# Jackson JSON 라이브러리
alias(
    name = "jackson_databind",
    actual = "@maven//:com_fasterxml_jackson_core_jackson_databind",
)

alias(
    name = "jackson_kotlin",
    actual = "@maven//:com_fasterxml_jackson_module_jackson_module_kotlin",
)

# JWT
alias(
    name = "jjwt_api",
    actual = "@maven//:io_jsonwebtoken_jjwt_api",
)

# 데이터베이스
alias(
    name = "postgresql",
    actual = "@maven//:org_postgresql_postgresql",
)

alias(
    name = "hikaricp",
    actual = "@maven//:com_zaxxer_HikariCP",
)

# 로깅
alias(
    name = "slf4j_api",
    actual = "@maven//:org_slf4j_slf4j_api",
)

# 테스트
alias(
    name = "junit_jupiter_api",
    actual = "@maven//:org_junit_jupiter_junit_jupiter_api",
)

alias(
    name = "mockk",
    actual = "@maven//:io_mockk_mockk",
)
```

### 별칭 명명 규칙

```python
# 규칙: 라이브러리명_주요기능 (소문자, 언더스코어)
alias(
    name = "spring_boot_starter_web",
    actual = "@maven//:org_springframework_boot_spring_boot_starter_web",
)

alias(
    name = "jackson_databind",
    actual = "@maven//:com_fasterxml_jackson_core_jackson_databind",
)

# 피해야 할 명명
alias(
    name = "SpringBootStarterWeb",         # 케이스 불일치
    actual = "@maven//:org_springframework_boot_spring_boot_starter_web",
)

alias(
    name = "sbsw",                         # 너무 축약됨
    actual = "@maven//:org_springframework_boot_spring_boot_starter_web",
)
```

## 참고 자료

### Bazel 공식 문서
- **Bazel 공식 문서**: https://bazel.build/concepts/dependencies
- **Bazel Module 시스템 (bzlmod)**: https://bazel.build/external/module
- **Module Extensions**: https://bazel.build/external/extension
- **External Dependencies**: https://bazel.build/external/overview

### 언어별 Rules
- **rules_jvm_external** (Maven): https://github.com/bazelbuild/rules_jvm_external
- **rules_kotlin**: https://github.com/bazelbuild/rules_kotlin
- **rules_go**: https://github.com/bazelbuild/rules_go
- **Gazelle** (Go 의존성 자동화): https://github.com/bazelbuild/bazel-gazelle

### One-Version Rule 원론
- **Google의 One-Version Rule**: https://opensource.google/documentation/reference/thirdparty/oneversion
- **Software Engineering at Google** (Chapter 21: Dependency Management): https://abseil.io/resources/swe-book/html/ch21.html
- **Abseil Live at Head Philosophy**: https://abseil.io/about/philosophy#upgrade-support

---

## 요약

**One-Version Rule**은 모노레포에서 확장성과 안정성을 동시에 달성하기 위한 필수 원칙입니다.

### EntryDSM 프로젝트의 의존성 관리 구조:

```
MODULE.bazel (진입점)
  ├── go.MODULE.bazel (Go 전용)
  ├── kotlin.MODULE.bazel (Kotlin 전용)
  └── Extension 파일들
      ├── go.bzl (Go 의존성)
      ├── kotlin.bzl (Kotlin 의존성)
      └── maven.bzl (Maven 의존성)
          ↓
    루트 BUILD.bazel (별칭 정의)
          ↓
    systems/의 각 BUILD.bazel (선택적 사용)
```

### 핵심 원칙:

1. **중앙 집중식 버전 관리**: maven.bzl에서 모든 버전을 상수로 정의
2. **별칭을 통한 간편한 참조**: 긴 경로 대신 `//:jackson_databind` 사용
3. **선택적 의존성 사용**: 각 서비스는 필요한 것만 deps에 선언
4. **자동 동기화**: `bazel sync` 또는 빌드 시 자동으로 의존성 다운로드
