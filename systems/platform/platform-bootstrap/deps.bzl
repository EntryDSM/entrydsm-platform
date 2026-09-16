SPRING_DEPS = [
    "@maven//:org_springframework_boot_spring_boot_starter_web",
    "@maven//:org_springframework_boot_spring_boot_starter_actuator",
    "@maven//:org_springframework_boot_spring_boot_starter_data_jpa",
    "@maven//:org_springframework_boot_spring_boot_jpa",
    "@maven//:org_flywaydb_flyway_core",
    "@maven//:org_flywaydb_flyway_mysql",
    "@maven//:com_mysql_mysql_connector_j",
]

KOTLIN_DEPS = [
    "@maven//:org_jetbrains_kotlin_kotlin_reflect",
    "@maven//:tools_jackson_module_jackson_module_kotlin",
    "@maven//:software_amazon_awssdk_s3",
]

TEST_DEPS = [
    "@maven//:junit_junit",
]

# 이 프로세스에 올라가는 모듈. 모듈을 추가하면 PlatformApplication 의 @Import 목록도 같이 고친다.
MODULE_TARGETS = [
    "//systems/identity/identity-bootstrap:main",
    "//systems/application/application-bootstrap:main",
    "//systems/configuration/configuration-bootstrap:main",
    "//systems/notification/notification-bootstrap:main",
    "//systems/admin/admin-bootstrap:main",
    "//systems/observability/observability-bootstrap:main",
]

MODULE_DEPS = SPRING_DEPS + KOTLIN_DEPS
