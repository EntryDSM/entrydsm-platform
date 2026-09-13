KOTLIN_DEPS = [
    "@maven//:org_springframework_boot_spring_boot_starter_data_jpa",
    "@maven//:org_springframework_spring_tx",
    "@maven//:com_mysql_mysql_connector_j",
    "@maven//:io_github_openhtmltopdf_openhtmltopdf_core",
    "@maven//:io_github_openhtmltopdf_openhtmltopdf_pdfbox",
    "@maven//:software_amazon_awssdk_s3",
    "@maven//:io_grpc_grpc_netty_shaded",
    "@maven//:io_grpc_grpc_protobuf",
    "@maven//:io_grpc_grpc_stub",
    "@maven//:javax_annotation_javax_annotation_api",
    "//contracts:application_grpc_java",
    "//contracts:application_java_proto",
    "//contracts:notification_grpc_java",
    "//contracts:notification_java_proto",
    "//systems/admin/admin-domain:main",
]

TEST_DEPS = [
    "@maven//:junit_junit",
]

MODULE_DEPS = KOTLIN_DEPS
