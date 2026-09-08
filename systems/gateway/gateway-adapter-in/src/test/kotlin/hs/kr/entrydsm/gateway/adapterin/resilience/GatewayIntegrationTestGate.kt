package hs.kr.entrydsm.gateway.adapterin.resilience

import org.junit.jupiter.api.Assumptions

internal object GatewayIntegrationTestGate {
    const val REQUIRED_ENVIRONMENT_VARIABLE = "GATEWAY_INTEGRATION_REQUIRED"

    fun isRequired(environment: (String) -> String? = { name -> System.getenv(name) }): Boolean =
        environment(REQUIRED_ENVIRONMENT_VARIABLE).equals("true", ignoreCase = true)

    fun unavailable(
        message: String,
        cause: Throwable,
        required: Boolean = isRequired(),
    ): Nothing {
        if (required) {
            throw AssertionError(message, cause)
        }
        Assumptions.assumeTrue(false, "$message (${cause.message})")
        throw AssertionError(message, cause)
    }
}
