KOTLIN_DEPS = [
    "@maven//:org_springframework_boot_spring_boot_starter_data_jpa",
    "@maven//:com_mysql_mysql_connector_j",
    "@maven//:io_github_openhtmltopdf_openhtmltopdf_core",
    "@maven//:io_github_openhtmltopdf_openhtmltopdf_pdfbox",
    "@maven//:io_grpc_grpc_netty_shaded",
    "@maven//:io_grpc_grpc_protobuf",
    "@maven//:io_grpc_grpc_stub",
    "@maven//:javax_annotation_javax_annotation_api",
    "@maven//:software_amazon_awssdk_s3",
    "//contracts:notification_grpc_java",
    "//contracts:notification_java_proto",
    "//systems/admin/admin-domain:main",
]

TEST_DEPS = [
    "@maven//:junit_junit",
]

MODULE_DEPS = KOTLIN_DEPS
