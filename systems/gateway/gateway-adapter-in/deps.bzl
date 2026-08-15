KOTLIN_DEPS = []

TEST_DEPS = [
    "@maven//:org_junit_jupiter_junit_jupiter",
    "@maven//:org_junit_platform_junit_platform_launcher",
    "@maven//:org_springframework_boot_spring_boot_starter_test",
]

MODULE_DEPS = KOTLIN_DEPS + [
    "@maven//:com_fasterxml_jackson_core_jackson_databind",
    "@maven//:org_springframework_boot_spring_boot_starter_actuator",
    "@maven//:org_springframework_boot_spring_boot_starter_data_redis",
    "@maven//:org_springframework_cloud_spring_cloud_starter_gateway_server_webflux",
    "@maven//:io_github_resilience4j_resilience4j_circuitbreaker",
    "//systems/gateway/gateway-application:main",
    "//systems/gateway/gateway-domain:main",
]
