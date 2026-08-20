SPRING_DEPS = [
    "@maven//:org_springframework_boot_spring_boot_starter_actuator",
    "@maven//:org_springframework_cloud_spring_cloud_starter_gateway_server_webflux",
]

KOTLIN_DEPS = [
    "@maven//:org_jetbrains_kotlin_kotlin_reflect",
    "@maven//:com_fasterxml_jackson_module_jackson_module_kotlin",
]

TEST_DEPS = [
    "@maven//:org_springframework_boot_spring_boot_starter_test",
    "@maven//:org_junit_jupiter_junit_jupiter",
    "@maven//:org_junit_platform_junit_platform_launcher",
]

MODULE_DEPS = SPRING_DEPS + KOTLIN_DEPS
