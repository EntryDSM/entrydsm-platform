KOTLIN_DEPS = [
    "@maven//:io_grpc_grpc_protobuf",
    "@maven//:io_grpc_grpc_stub",
    "@maven//:javax_annotation_javax_annotation_api",
    "//contracts:identity_grpc_java",
    "//contracts:identity_java_proto",
    "@maven//:org_springframework_boot_spring_boot_starter_web",
    "@maven//:org_springframework_boot_spring_boot_starter_validation",
    "@maven//:org_springframework_boot_spring_boot_starter_security",
    "//systems/identity/identity-application:main",
    "//systems/identity/identity-domain:main",
]

TEST_DEPS = [
    "@maven//:junit_junit",
]

MODULE_DEPS = KOTLIN_DEPS
