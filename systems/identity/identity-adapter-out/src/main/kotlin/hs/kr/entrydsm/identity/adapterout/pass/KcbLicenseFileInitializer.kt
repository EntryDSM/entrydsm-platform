package hs.kr.entrydsm.identity.adapterout.pass

import jakarta.annotation.PostConstruct
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
) {
    @PostConstruct
    fun initialize() {
        if (licenseFileUrl.isBlank()) return
        val target = Path.of(licenseFilePath)
        try {
            target.parent?.let(Files::createDirectories)
            URI(licenseFileUrl).toURL().openStream().use { input ->
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (exception: Exception) {
            throw IllegalStateException("KCB license file could not be initialized.", exception)
        }
    }
}
