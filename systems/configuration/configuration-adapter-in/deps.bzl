KOTLIN_DEPS = [
    "@maven//:org_springframework_boot_spring_boot_starter",
    "@maven//:org_springframework_boot_spring_boot_starter_web",
    "@maven//:org_springframework_boot_spring_boot_starter_validation",
    "@maven//:tools_jackson_module_jackson_module_kotlin",
    "//systems/configuration/configuration-domain:main",
]

TEST_DEPS = [
    "@maven//:junit_junit",
    "@maven//:org_springframework_boot_spring_boot_starter_test",
]

MODULE_DEPS = KOTLIN_DEPS
