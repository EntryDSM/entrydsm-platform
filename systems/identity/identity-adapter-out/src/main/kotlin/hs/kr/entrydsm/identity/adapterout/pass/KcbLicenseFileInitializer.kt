package hs.kr.entrydsm.identity.adapterout.pass

import jakarta.annotation.PostConstruct
import java.io.IOException
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration

/** Downloads the KCB license only when an operator explicitly configures a URL. */
@Configuration(proxyBeanMethods = false)
class KcbLicenseFileInitializer(
    @Value("\${pass.license-file-url:}") private val licenseFileUrl: String,
    @Value("\${pass.license-file-path:/tmp/V61290000000_IDS_01_PROD_AES_license.dat}")
    private val licenseFilePath: String,
    @Value("\${pass.connect-timeout-ms:5000}") private val connectTimeoutMs: Int,
    @Value("\${pass.read-timeout-ms:10000}") private val readTimeoutMs: Int,
) {
    init {
        require(connectTimeoutMs > 0) { "PASS license connect timeout must be positive." }
        require(readTimeoutMs > 0) { "PASS license read timeout must be positive." }
    }

    @PostConstruct
    fun initialize() {
        if (licenseFileUrl.isBlank()) return
        val uri = URI(licenseFileUrl)
        require(uri.scheme.equals("https", ignoreCase = true)) {
            "KCB license file URL must use HTTPS."
        }
        val target = Path.of(licenseFilePath)
        try {
            target.parent?.let(Files::createDirectories)
            val connection = uri.toURL().openConnection().apply {
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
            }
            connection.getInputStream().use { input ->
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (exception: IOException) {
            throw IllegalStateException("KCB license file could not be initialized.", exception)
        }
    }
}
