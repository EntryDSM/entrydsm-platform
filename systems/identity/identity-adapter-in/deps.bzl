KOTLIN_DEPS = [
    "@maven//:org_springframework_boot_spring_boot_starter_web",
    "//systems/identity/identity-application:main",
]

TEST_DEPS = [
    "@maven//:junit_junit",
]

MODULE_DEPS = KOTLIN_DEPS
