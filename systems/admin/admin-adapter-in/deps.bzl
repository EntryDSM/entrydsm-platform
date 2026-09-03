KOTLIN_DEPS = [
    "@maven//:org_springframework_boot_spring_boot_starter_web",
    "@maven//:org_springframework_boot_spring_boot_starter_validation",
    "@maven//:com_fasterxml_jackson_core_jackson_annotations",
    "//systems/admin/admin-application:main",
    "//systems/admin/admin-domain:main",
]

TEST_DEPS = [
    "@maven//:junit_junit",
]

MODULE_DEPS = KOTLIN_DEPS
