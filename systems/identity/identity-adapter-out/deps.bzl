KOTLIN_DEPS = [
    "@maven//:io_grpc_grpc_netty_shaded",
    "@maven//:io_grpc_grpc_protobuf",
    "@maven//:io_grpc_grpc_stub",
    "@maven//:javax_annotation_javax_annotation_api",
    "@maven//:org_springframework_boot_spring_boot_starter_data_redis",
    "@maven//:org_springframework_security_spring_security_crypto",
    "@maven//:org_springframework_boot_spring_boot_starter_data_jpa",
    "@maven//:com_mysql_mysql_connector_j",
    "//contracts:application_grpc_java",
    "//contracts:application_java_proto",
    "//systems/identity/identity-application:main",
    "//systems/identity/identity-domain:main",
]

TEST_DEPS = [
    "@maven//:org_jetbrains_kotlin_kotlin_reflect",
    "@maven//:junit_junit",
    "@maven//:org_testcontainers_testcontainers",
    "@maven//:org_springframework_boot_spring_boot_persistence",
    "@maven//:org_springframework_boot_spring_boot_starter_test",
]

MODULE_DEPS = KOTLIN_DEPS
