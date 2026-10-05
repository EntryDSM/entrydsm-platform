KOTLIN_DEPS = [
    "//systems/admin/admin-domain:main",
    "//contracts:application_grpc_java",
    "//contracts:application_java_proto",
    "//contracts:notification_grpc_java",
    "//contracts:notification_java_proto",
    "@maven//:org_springframework_boot_spring_boot_starter",
    "@maven//:org_springframework_spring_web",
    "@maven//:org_springframework_boot_spring_boot_starter_web",
    "@maven//:io_grpc_grpc_netty_shaded",
    "@maven//:io_grpc_grpc_protobuf",
    "@maven//:io_grpc_grpc_stub",
    "@maven//:com_google_protobuf_protobuf_java",
    "@maven//:javax_annotation_javax_annotation_api",
    "@maven//:tools_jackson_core_jackson_databind",
    "@maven//:tools_jackson_module_jackson_module_kotlin",
]
TEST_DEPS = ["@maven//:junit_junit"]
MODULE_DEPS = KOTLIN_DEPS
