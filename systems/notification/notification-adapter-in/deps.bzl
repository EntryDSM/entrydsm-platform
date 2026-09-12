# Dependencies for the notification REST and gRPC adapter module.
KOTLIN_DEPS = [
    "@maven//:io_grpc_grpc_protobuf",
    "@maven//:io_grpc_grpc_stub",
    "@maven//:javax_annotation_javax_annotation_api",
    "@maven//:org_springframework_boot_spring_boot_starter_web",
    "//contracts:notification_grpc_java",
    "//contracts:notification_java_proto",
    "//systems/notification/notification-application:main",
    "//systems/notification/notification-domain:main",
]

TEST_DEPS = [
    "@maven//:junit_junit",
]

MODULE_DEPS = KOTLIN_DEPS
