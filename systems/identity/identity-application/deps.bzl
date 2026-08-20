KOTLIN_DEPS = [
    "//systems/identity/identity-domain:main",
    "@maven//:io_jsonwebtoken_jjwt_api",
    "@maven//:io_jsonwebtoken_jjwt_impl",
    "@maven//:io_jsonwebtoken_jjwt_jackson",
]

TEST_DEPS = [
    "@maven//:junit_junit",
    "@maven//:org_springframework_boot_spring_boot_starter_test",
]

MODULE_DEPS = KOTLIN_DEPS
