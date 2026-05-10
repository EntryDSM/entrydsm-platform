KOTLIN_DEPS = [
    "@maven//:org_springframework_boot_spring_boot_starter_data_jpa",
    "@maven//:com_mysql_mysql_connector_j",
    "//systems/configuration/configuration-domain:main",
]

TEST_DEPS = [
    "@maven//:junit_junit",
]

MODULE_DEPS = KOTLIN_DEPS
