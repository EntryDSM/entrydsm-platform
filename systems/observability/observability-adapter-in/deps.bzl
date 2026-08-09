KOTLIN_DEPS = [
    "@maven//:org_springframework_boot_spring_boot_starter_web",
    "@maven//:org_springframework_boot_spring_boot_starter_validation",
    "@maven//:io_jsonwebtoken_jjwt_api",
    "@maven//:io_jsonwebtoken_jjwt_impl",
    "@maven//:io_jsonwebtoken_jjwt_jackson",
    "@maven//:org_apache_poi_poi",
    "@maven//:org_apache_poi_poi_ooxml",
    "//systems/observability/observability-application:main",
    "//systems/observability/observability-domain:main",
]

TEST_DEPS = [
    "@maven//:junit_junit",
]

MODULE_DEPS = KOTLIN_DEPS
