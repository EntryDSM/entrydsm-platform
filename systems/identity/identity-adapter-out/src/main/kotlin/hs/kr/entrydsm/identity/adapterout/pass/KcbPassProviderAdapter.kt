package hs.kr.entrydsm.identity.adapterout.pass

import hs.kr.entrydsm.identity.application.port.out.PassIdentity
import hs.kr.entrydsm.identity.application.port.out.PassProviderException
import hs.kr.entrydsm.identity.application.port.out.PassProviderPort
import kcb.module.v3.OkCert
import kcb.module.v3.exception.OkCertException
import kcb.org.json.JSONObject
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/** KCB OkCert v3 adapter based on the popup flow used by Casper-User. */
@Component
class KcbPassProviderAdapter(
    @Value("\${pass.target:PROD}") private val target: String,
    @Value("\${pass.cp-code:}") private val cpCode: String,
    @Value("\${pass.license:}") private val license: String,
    @Value("\${pass.license-file-path:/tmp/V61290000000_IDS_01_PROD_AES_license.dat}")
    private val licenseFilePath: String,
    @Value("\${pass.site-name:EntryDSM}") private val siteName: String,
    @Value("\${pass.site-url:https://entrydsm.kr}") private val siteUrl: String,
    @Value("\${pass.popup-url:}") private val popupUrl: String,
    @Value("\${pass.connect-timeout-ms:5000}") private val connectTimeoutMs: Int,
    @Value("\${pass.read-timeout-ms:10000}") private val readTimeoutMs: Int,
) : PassProviderPort {
    override fun generatePopup(redirectUrl: String): String {
        validateConfiguration()
        val request = JSONObject()
            .put("RETURN_URL", redirectUrl)
            .put("SITE_NAME", siteName)
            .put("SITE_URL", siteUrl)
            .put("RQST_CAUS_CD", REQUEST_CAUSE_CODE)
        val result = call(START_SERVICE, request.toString())
        val resultCode = result.optString(RESULT_CODE)
        val resultMessage = result.optString(RESULT_MESSAGE)
        val modelToken = popupModelToken(resultCode, result.optString(MODEL_TOKEN))
        return popupHtml(
            modelToken = modelToken.takeIf { resultCode == SUCCESS_CODE },
            resultCode = resultCode,
            resultMessage = resultMessage,
        )
    }

    override fun verify(token: String): PassIdentity {
        validateConfiguration()
        val result = call(RESULT_SERVICE, JSONObject().put(MODEL_TOKEN, token).toString())
        if (result.optString(RESULT_CODE) != SUCCESS_CODE) {
            throw PassProviderException(PassProviderException.Reason.INVALID_RESPONSE)
        }
        val phoneNumber = result.optString(PHONE_NUMBER)
        val name = result.optString(RESULT_NAME)
        if (phoneNumber.isBlank() || name.isBlank()) {
            throw PassProviderException(PassProviderException.Reason.INVALID_RESPONSE)
        }
        return PassIdentity(phoneNumber, name)
    }

    private fun call(serviceName: String, request: String): JSONObject = try {
        val okCert = OkCert().apply {
            setConnectTimeout(connectTimeoutMs)
            setReadTimeout(readTimeoutMs)
        }
        JSONObject(okCert.callOkCert(target, cpCode, serviceName, effectiveLicense(), request))
    } catch (exception: OkCertException) {
        throw PassProviderException(PassProviderException.Reason.UNAVAILABLE, exception)
    } catch (exception: RuntimeException) {
        throw PassProviderException(PassProviderException.Reason.INVALID_RESPONSE, exception)
    }

    private fun validateConfiguration() {
        if (target.isBlank() || cpCode.isBlank() || effectiveLicense().isBlank() || popupUrl.isBlank() ||
            connectTimeoutMs <= 0 || readTimeoutMs <= 0
        ) {
            throw PassProviderException(PassProviderException.Reason.UNAVAILABLE)
        }
    }

    private fun effectiveLicense(): String = license.ifBlank { licenseFilePath }

    private fun popupHtml(modelToken: String?, resultCode: String, resultMessage: String): String {
        val submitScript = if (modelToken != null) {
            "request();"
        } else {
            "alert('${htmlEscape(resultCode)} : ${htmlEscape(resultMessage)}'); self.close();"
        }
        return """
            <!doctype html>
            <html lang="ko">
            <head>
              <meta charset="utf-8">
              <title>EntryDSM PASS 본인인증</title>
              <script>
                function request() {
                  document.form1.action = '${javascriptEscape(popupUrl)}';
                  document.form1.method = 'post';
                  document.form1.submit();
                }
              </script>
            </head>
            <body>
              <form name="form1">
                <input type="hidden" name="tc" value="${htmlEscape(CERT_CHOICE_COMMAND)}">
                <input type="hidden" name="cp_cd" value="${htmlEscape(cpCode)}">
                <input type="hidden" name="mdl_tkn" value="${htmlEscape(modelToken.orEmpty())}">
                <input type="hidden" name="target_id" value="">
              </form>
              <script>$submitScript</script>
            </body>
            </html>
        """.trimIndent()
    }

    private fun htmlEscape(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")

    private fun javascriptEscape(value: String): String = htmlEscape(value)
        .replace("\\", "\\\\")
        .replace("\n", "\\n")
        .replace("\r", "\\r")

    private companion object {
        const val START_SERVICE = "IDS_HS_POPUP_START"
        const val RESULT_SERVICE = "IDS_HS_POPUP_RESULT"
        const val REQUEST_CAUSE_CODE = "00"
        const val SUCCESS_CODE = "B000"
        const val RESULT_CODE = "RSLT_CD"
        const val RESULT_MESSAGE = "RSLT_MSG"
        const val MODEL_TOKEN = "MDL_TKN"
        const val RESULT_NAME = "RSLT_NAME"
        const val PHONE_NUMBER = "TEL_NO"
        const val CERT_CHOICE_COMMAND = "kcb.oknm.online.safehscert.popup.cmd.P931_CertChoiceCmd"
    }
}

fun popupModelToken(resultCode: String, modelToken: String): String? {
    if (resultCode == "B000" && modelToken.isBlank()) {
        throw PassProviderException(PassProviderException.Reason.INVALID_RESPONSE)
    }
    return modelToken.takeIf { resultCode == "B000" }
}
