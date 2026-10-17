KOTLIN_DEPS = [
    "//systems/application/application-application:main",
    "//systems/application/application-domain:main",
    "//systems/configuration/configuration-domain:main",
    "@maven//:org_springframework_boot_spring_boot_starter",
    "@maven//:org_springframework_spring_tx",
    "//systems/admin/admin-domain:main",
]

TEST_DEPS = [
    "//systems/configuration/configuration-application:main",
    "@maven//:junit_junit",
]

MODULE_DEPS = KOTLIN_DEPS
