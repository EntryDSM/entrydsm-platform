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

def setup_spring_allopen_plugin():
    kt_compiler_plugin(
        name = "spring_allopen",
        id = "org.jetbrains.kotlin.allopen",
        options = {"preset": "spring"},
        deps = ["@rules_kotlin//kotlin/compiler:allopen-compiler-plugin"],
    )

def setup_jpa_allopen_plugin():
    kt_plugin_cfg(
        name = "jpa_entity_allopen",
        plugin = ":spring_allopen",
        options = {"annotation": ["jakarta.persistence.Entity"]},
    )

    kt_plugin_cfg(
        name = "jpa_mapped_superclass_allopen",
        plugin = ":spring_allopen",
        options = {"annotation": ["jakarta.persistence.MappedSuperclass"]},
    )

    kt_plugin_cfg(
        name = "jpa_embeddable_allopen",
        plugin = ":spring_allopen",
        options = {"annotation": ["jakarta.persistence.Embeddable"]},
    )

def jpa_allopen_plugins():
    return [
        "//:spring_allopen",
        "//:jpa_entity_allopen",
        "//:jpa_mapped_superclass_allopen",
        "//:jpa_embeddable_allopen",
    ]
