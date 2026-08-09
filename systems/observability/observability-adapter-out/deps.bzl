KOTLIN_DEPS = [
    "@maven//:org_springframework_boot_spring_boot_starter_data_redis",
    "//systems/observability/observability-application:main",
    "//systems/observability/observability-domain:main",
]

TEST_DEPS = [
    "@maven//:junit_junit",
]

MODULE_DEPS = KOTLIN_DEPS
