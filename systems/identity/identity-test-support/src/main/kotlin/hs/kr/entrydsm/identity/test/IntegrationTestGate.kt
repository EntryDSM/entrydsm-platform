package hs.kr.entrydsm.identity.test

object IntegrationTestGate {
    const val REQUIRED_ENVIRONMENT_VARIABLE = "IDENTITY_INTEGRATION_REQUIRED"

    @JvmStatic
    fun isRequired(environment: (String) -> String? = { System.getenv(it) }): Boolean =
        environment(REQUIRED_ENVIRONMENT_VARIABLE).equals("true", ignoreCase = true)
}
