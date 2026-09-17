KOTLIN_DEPS = [
    "@maven//:org_springframework_spring_tx",
    "@maven//:io_grpc_grpc_protobuf",
    "@maven//:io_grpc_grpc_stub",
    "@maven//:javax_annotation_javax_annotation_api",
    "@maven//:org_springframework_boot_spring_boot_starter_web",
    "@maven//:org_springframework_boot_spring_boot_starter_validation",
    "//contracts:application_grpc_java",
    "//contracts:application_java_proto",
    "//systems/application/application-application:main",
    "//systems/application/application-domain:main",
]

TEST_DEPS = [
    "@maven//:io_grpc_grpc_netty_shaded",
    "@maven//:junit_junit",
    "@maven//:org_springframework_spring_test",
]

MODULE_DEPS = KOTLIN_DEPS
