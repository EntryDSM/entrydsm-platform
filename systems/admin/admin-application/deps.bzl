KOTLIN_DEPS = [
    "@maven//:org_springframework_boot_spring_boot_starter",
    "@maven//:org_springframework_spring_tx",
    "//systems/admin/admin-domain:main",
]

TEST_DEPS = [
    "@maven//:junit_junit",
]

MODULE_DEPS = KOTLIN_DEPS
