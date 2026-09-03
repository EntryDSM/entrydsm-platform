load(
    "@rules_kotlin//kotlin:core.bzl",
    "kt_compiler_plugin",
    "kt_javac_options",
    "kt_kotlinc_options",
    "kt_plugin_cfg",
)

JAVA_VERSION = "17"

def setup_kotlin_compiler():
    kt_javac_options(name = "javac_options", release = JAVA_VERSION)
    kt_kotlinc_options(name = "kotlinc_options", jvm_target = JAVA_VERSION)

def setup_spring_allopen_plugin(target_name = "spring_allopen"):
    kt_compiler_plugin(
        name = target_name,
        id = "org.jetbrains.kotlin.allopen",
        options = {"preset": "spring"},
        deps = ["@rules_kotlin//kotlin/compiler:allopen-compiler-plugin"],
    )

def setup_jpa_noarg_plugin():
    """JPA용 Kotlin no-arg 컴파일러 플러그인을 등록한다.

    JPA 엔티티는 인자 없는 생성자를 요구한다. Kotlin 클래스에는 없으므로 컴파일 시 만들어 준다.
    """
    kt_compiler_plugin(
        name = "jpa_noarg",
        id = "org.jetbrains.kotlin.noarg",
        options = {"preset": "jpa"},
        deps = ["@rules_kotlin//kotlin/compiler:noarg-compiler-plugin"],
    )

def setup_jpa_allopen_plugin(target_name = "spring_allopen"):
    kt_plugin_cfg(
        name = target_name + "_entity_allopen",
        plugin = ":" + target_name,
        options = {"annotation": ["jakarta.persistence.Entity"]},
    )

    kt_plugin_cfg(
        name = target_name + "_mapped_superclass_allopen",
        plugin = ":" + target_name,
        options = {"annotation": ["jakarta.persistence.MappedSuperclass"]},
    )

    kt_plugin_cfg(
        name = target_name + "_embeddable_allopen",
        plugin = ":" + target_name,
        options = {"annotation": ["jakarta.persistence.Embeddable"]},
    )

def jpa_allopen_plugins(target_name = "spring_allopen"):
    return [
        "//:" + target_name,
        "//:" + target_name + "_entity_allopen",
        "//:" + target_name + "_mapped_superclass_allopen",
        "//:" + target_name + "_embeddable_allopen",
    ]
