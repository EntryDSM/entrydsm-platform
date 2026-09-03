KOTLIN_DEPS = [
    "@maven//:org_springframework_boot_spring_boot_starter_data_redis",
    "@maven//:org_springframework_security_spring_security_crypto",
    "@maven//:org_springframework_boot_spring_boot_starter_data_jpa",
    "@maven//:com_mysql_mysql_connector_j",
    "//systems/identity/identity-application:main",
    "//systems/identity/identity-domain:main",
]

TEST_DEPS = [
    "@maven//:junit_junit",
    "@maven//:org_testcontainers_testcontainers",
    "@maven//:org_springframework_boot_spring_boot_persistence",
    "@maven//:org_springframework_boot_spring_boot_starter_test",
]

MODULE_DEPS = KOTLIN_DEPS
