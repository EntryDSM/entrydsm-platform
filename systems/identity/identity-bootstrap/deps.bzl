SPRING_DEPS = [
    "@maven//:org_springframework_boot_spring_boot_starter_web",
    "@maven//:org_springframework_boot_spring_boot_starter_actuator",
]

KOTLIN_DEPS = [
    "@maven//:org_jetbrains_kotlin_kotlin_reflect",
    "@maven//:com_fasterxml_jackson_module_jackson_module_kotlin",
]

TEST_DEPS = [
    "@maven//:org_springframework_boot_spring_boot_starter_test",
    "@maven//:junit_junit",
]

MODULE_DEPS = SPRING_DEPS + KOTLIN_DEPS
