SPRING_DEPS = [
    "@maven//:org_springframework_boot_spring_boot_starter_web",
    "@maven//:org_springframework_boot_spring_boot_starter_validation",
    "@maven//:org_springframework_boot_spring_boot_starter_data_jpa",
    "@maven//:org_springframework_boot_spring_boot_starter_actuator",
    "@maven//:com_mysql_mysql_connector_j",
]

KOTLIN_DEPS = [
    "@maven//:org_jetbrains_kotlin_kotlin_reflect",
    "@maven//:com_fasterxml_jackson_module_jackson_module_kotlin",
    "@maven//:software_amazon_awssdk_s3",
]

TEST_DEPS = [
    "@maven//:org_springframework_boot_spring_boot_starter_test",
    "@maven//:junit_junit",
]

MODULE_DEPS = SPRING_DEPS + KOTLIN_DEPS
