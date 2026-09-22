KOTLIN_DEPS = [
    "@maven//:org_springframework_boot_spring_boot_starter_data_jpa",
    "@maven//:com_mysql_mysql_connector_j",
    "@maven//:org_apache_pdfbox_pdfbox",
    "@maven//:org_apache_poi_poi",
    "@maven//:org_apache_poi_poi_ooxml",
    "@maven//:io_grpc_grpc_netty_shaded",
    "@maven//:io_grpc_grpc_protobuf",
    "@maven//:io_grpc_grpc_stub",
    "@maven//:com_google_protobuf_protobuf_java",
    "@maven//:javax_annotation_javax_annotation_api",
    "@maven//:software_amazon_awssdk_s3",
    "@maven//:com_fasterxml_jackson_core_jackson_databind",
    "//contracts:application_grpc_java",
    "//contracts:application_java_proto",
    "//contracts:configuration_grpc_java",
    "//contracts:configuration_java_proto",
    "//contracts:notification_grpc_java",
    "//contracts:notification_java_proto",
    "//systems/admin/admin-domain:main",
]

TEST_DEPS = [
    "@maven//:junit_junit",
]

MODULE_DEPS = KOTLIN_DEPS
