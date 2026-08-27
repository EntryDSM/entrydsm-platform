KOTLIN_DEPS = [
    "@maven//:io_grpc_grpc_netty_shaded",
    "@maven//:io_grpc_grpc_protobuf",
    "@maven//:io_grpc_grpc_stub",
    "@maven//:io_grpc_grpc_kotlin_stub",
    "@maven//:com_google_protobuf_protobuf_java",
    "@maven//:com_google_protobuf_protobuf_kotlin",
    "@maven//:javax_annotation_javax_annotation_api",
    "@maven//:org_springframework_boot_spring_boot_starter",
    "@maven//:org_springframework_boot_spring_boot_starter_web",
    "@maven//:org_springframework_boot_spring_boot_starter_validation",
    "@maven//:com_fasterxml_jackson_module_jackson_module_kotlin",
    "//contracts:configuration_grpc_java",
    "//contracts:configuration_java_proto",
    "//systems/configuration/configuration-domain:main",
]

TEST_DEPS = [
    "@maven//:junit_junit",
    "@maven//:org_springframework_boot_spring_boot_starter_test",
]

MODULE_DEPS = KOTLIN_DEPS
