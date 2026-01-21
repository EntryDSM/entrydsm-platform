load("@rules_kotlin//kotlin:core.bzl", "kt_compiler_plugin", "kt_javac_options", "kt_kotlinc_options")

JAVA_VERSION = "17"

def setup_kotlin_compiler():
    kt_javac_options(
        name = "javac_options",
        release = JAVA_VERSION,
    )

    kt_kotlinc_options(
        name = "kotlinc_options",
        jvm_target = JAVA_VERSION,
    )

def setup_spring_allopen_plugin():
    kt_compiler_plugin(
        name = "spring_allopen",
        id = "org.jetbrains.kotlin.allopen",
        options = {
            "preset": "spring",
        },
        deps = [
            "@maven//:org_jetbrains_kotlin_kotlin_allopen_compiler_plugin",
        ],
    )
