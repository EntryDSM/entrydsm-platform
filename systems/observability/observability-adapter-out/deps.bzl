KOTLIN_DEPS = [
    "@maven//:org_springframework_boot_spring_boot_starter_data_redis",
    "@maven//:com_fasterxml_jackson_core_jackson_databind",
    "@maven//:org_apache_poi_poi",
    "@maven//:org_apache_poi_poi_ooxml",
    "//systems/observability/observability-application:main",
    "//systems/observability/observability-domain:main",
]

TEST_DEPS = [
    "@maven//:junit_junit",
]

MODULE_DEPS = KOTLIN_DEPS
