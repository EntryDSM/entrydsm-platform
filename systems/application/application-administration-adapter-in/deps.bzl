MODULE_DEPS = [
    "//systems/application/application-application:main",
    "//systems/configuration/configuration-domain:main",
    "@maven//:org_springframework_boot_spring_boot_starter_web",
    "//systems/application/application-administration-domain:main",
    "//contracts:application_grpc_java",
    "//contracts:application_java_proto",
    "@maven//:org_springframework_boot_spring_boot_starter",
    "@maven//:io_grpc_grpc_stub",
    "@maven//:io_grpc_grpc_protobuf",
    "@maven//:tools_jackson_core_jackson_databind",
    "@maven//:tools_jackson_module_jackson_module_kotlin",
]
TEST_DEPS = ["//systems/configuration/configuration-adapter-in:main", "@maven//:junit_junit", "@maven//:org_springframework_boot_spring_boot_starter_test"]
