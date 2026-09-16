# notification 의 REST 컨트롤러와 모듈 공개 API 구현이 쓰는 의존성.
KOTLIN_DEPS = [
    "@maven//:org_springframework_boot_spring_boot_starter_web",
    "//systems/notification/notification-api:main",
    "//systems/notification/notification-application:main",
    "//systems/notification/notification-domain:main",
]

TEST_DEPS = [
    "@maven//:junit_junit",
]

MODULE_DEPS = KOTLIN_DEPS
