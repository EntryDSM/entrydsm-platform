KOTLIN_DEPS = [
    "@maven//:io_grpc_grpc_netty_shaded",
    "@maven//:io_grpc_grpc_protobuf",
    "@maven//:io_grpc_grpc_stub",
    "@maven//:io_grpc_grpc_kotlin_stub",
    "@maven//:com_google_protobuf_protobuf_java",
    "@maven//:com_google_protobuf_protobuf_kotlin",
    "@maven//:javax_annotation_javax_annotation_api",
    "@maven//:org_springframework_boot_spring_boot_starter",
    "//contracts:configuration_grpc_java",
    "//contracts:configuration_java_proto",
    "//systems/configuration/configuration-domain:main",
]

TEST_DEPS = [
    "@maven//:junit_junit",
]

MODULE_DEPS = KOTLIN_DEPS
