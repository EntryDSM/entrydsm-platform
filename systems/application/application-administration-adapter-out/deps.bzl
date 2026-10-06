KOTLIN_DEPS = [
    "@maven//:io_github_openhtmltopdf_openhtmltopdf_core",
    "@maven//:io_github_openhtmltopdf_openhtmltopdf_pdfbox",
    "@maven//:org_apache_pdfbox_pdfbox",
    "//systems/application/application-application:main",
    "//systems/application/application-adapter-out:main",
    "//systems/application/application-grpc-mapping:main",
    "//systems/configuration/configuration-domain:main",
    "@maven//:tools_jackson_module_jackson_module_kotlin",
    "@maven//:org_springframework_spring_web",
    "//systems/common/common-crypto:main",
    "@maven//:org_springframework_boot_spring_boot_starter_data_jpa",
    "@maven//:com_mysql_mysql_connector_j",
    "@maven//:org_apache_poi_poi",
    "@maven//:org_apache_poi_poi_ooxml",
    "@maven//:io_grpc_grpc_netty_shaded",
    "@maven//:io_grpc_grpc_protobuf",
    "@maven//:io_grpc_grpc_stub",
    "@maven//:com_google_protobuf_protobuf_java",
    "@maven//:javax_annotation_javax_annotation_api",
    "@maven//:tools_jackson_core_jackson_databind",
    "//contracts:application_grpc_java",
    "//contracts:application_java_proto",
    "//contracts:configuration_grpc_java",
    "//contracts:configuration_java_proto",
    "//systems/admin/admin-domain:main",
]

TEST_DEPS = [
    "//systems/application/application-administration-application:main",
    "@maven//:org_springframework_spring_test",
    "@maven//:org_springframework_boot_spring_boot_data_jpa_test",
    "@maven//:org_springframework_boot_spring_boot_test",
    "@maven//:org_jetbrains_kotlin_kotlin_reflect",
    "@maven//:com_h2database_h2",
    "@maven//:junit_junit",
]

MODULE_DEPS = KOTLIN_DEPS
