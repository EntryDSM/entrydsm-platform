KOTLIN_DEPS = [
    "@maven//:org_springframework_boot_spring_boot_starter_data_jpa",
    "@maven//:com_mysql_mysql_connector_j",
    "@maven//:io_github_openhtmltopdf_openhtmltopdf_core",
    "@maven//:io_github_openhtmltopdf_openhtmltopdf_pdfbox",
    "@maven//:software_amazon_awssdk_s3",
    "//systems/admin/admin-domain:main",
]

TEST_DEPS = [
    "@maven//:junit_junit",
]

MODULE_DEPS = KOTLIN_DEPS
