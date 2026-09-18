KOTLIN_DEPS = [
    "@maven//:org_springframework_boot_spring_boot_starter_data_jpa",
    "@maven//:org_springframework_boot_spring_boot_starter_data_redis",
    "@maven//:com_mysql_mysql_connector_j",
    "//contracts:application_java_proto",
    "//systems/application/application-application:main",
    "//systems/application/application-domain:main",
]

TEST_DEPS = [
    "@maven//:junit_junit",
    "@maven//:org_springframework_spring_test",
    "@maven//:org_springframework_boot_spring_boot_test",
    "@maven//:org_springframework_boot_spring_boot_test_autoconfigure",
    "@maven//:org_springframework_boot_spring_boot_data_jpa_test",
    "@maven//:com_h2database_h2",
]

MODULE_DEPS = KOTLIN_DEPS