KOTLIN_DEPS = [
    "@maven//:io_grpc_grpc_protobuf",
    "@maven//:io_grpc_grpc_stub",
    "@maven//:javax_annotation_javax_annotation_api",
    "@maven//:org_springframework_boot_spring_boot_starter_web",
    "//contracts:application_grpc_java",
    "//contracts:application_java_proto",
    "//systems/application/application-application:main",
    "//systems/application/application-domain:main",
]

TEST_DEPS = [
    "@maven//:io_grpc_grpc_netty_shaded",
    "@maven//:junit_junit",
]

MODULE_DEPS = KOTLIN_DEPS
