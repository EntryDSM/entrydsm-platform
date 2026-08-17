# Dependencies for the notification REST adapter module.
KOTLIN_DEPS = [
    "@maven//:org_springframework_boot_spring_boot_starter_web",
    "//systems/notification/notification-application:main",
    "//systems/notification/notification-domain:main",
]

TEST_DEPS = [
    "@maven//:junit_junit",
]

MODULE_DEPS = KOTLIN_DEPS
