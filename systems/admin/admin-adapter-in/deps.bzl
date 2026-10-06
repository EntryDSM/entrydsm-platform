KOTLIN_DEPS = [
    "@maven//:org_springframework_boot_spring_boot_starter_web",
    "@maven//:org_springframework_boot_spring_boot_starter_validation",
    "@maven//:com_fasterxml_jackson_core_jackson_annotations",
    "//systems/admin/admin-domain:main",
]

TEST_DEPS = [
    "@maven//:org_jetbrains_kotlin_kotlin_reflect",
    "@maven//:tools_jackson_module_jackson_module_kotlin",
    "@maven//:junit_junit",
    "@maven//:org_springframework_spring_test",
]

MODULE_DEPS = KOTLIN_DEPS
