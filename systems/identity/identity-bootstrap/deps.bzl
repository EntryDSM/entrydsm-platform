SPRING_DEPS = [
    "@maven//:org_springframework_boot_spring_boot_starter_web",
    "@maven//:org_springframework_boot_spring_boot_starter_security",
    "@maven//:org_springframework_boot_spring_boot_starter_actuator",
    "@maven//:org_springframework_boot_spring_boot_starter_flyway",
    "@maven//:org_flywaydb_flyway_mysql",
    "@maven//:org_springframework_boot_spring_boot_autoconfigure",
]

KOTLIN_DEPS = [
    "@maven//:org_jetbrains_kotlin_kotlin_reflect",
    "@maven//:com_fasterxml_jackson_module_jackson_module_kotlin",
    "@maven//:io_jsonwebtoken_jjwt_api",
    "@maven//:io_jsonwebtoken_jjwt_impl",
    "@maven//:io_jsonwebtoken_jjwt_jackson",
]

TEST_DEPS = [
    "@maven//:org_springframework_boot_spring_boot_starter_test",
    "@maven//:junit_junit",
    "@maven//:org_testcontainers_testcontainers",
]

MODULE_DEPS = SPRING_DEPS + KOTLIN_DEPS
