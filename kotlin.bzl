load("@rules_kotlin//kotlin:core.bzl", "kt_compiler_plugin", "kt_javac_options", "kt_kotlinc_options")

JAVA_VERSION = "17"

def setup_kotlin_compiler():
    kt_javac_options(name = "javac_options", release = JAVA_VERSION)
    kt_kotlinc_options(name = "kotlinc_options", jvm_target = JAVA_VERSION)

def setup_spring_allopen_plugin():
    kt_compiler_plugin(
        name = "spring_allopen",
        id = "org.jetbrains.kotlin.allopen",
        options = {"preset": "spring"},
        deps = ["@rules_kotlin//kotlin/compiler:allopen-compiler-plugin"],
    )

# JPA 엔티티는 인자 없는 생성자를 요구한다. Kotlin 클래스에는 없으므로 컴파일 시 만들어 준다.
def setup_jpa_noarg_plugin():
    kt_compiler_plugin(
        name = "jpa_noarg",
        id = "org.jetbrains.kotlin.noarg",
        options = {"preset": "jpa"},
        deps = ["@rules_kotlin//kotlin/compiler:noarg-compiler-plugin"],
    )
