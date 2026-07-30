package hs.kr.entrydsm.application.adapterin.web.dto.request

data class AddressRequest(
    val zipCode: String,
    val addressBase: String,
    val addressDetail: String,
)

