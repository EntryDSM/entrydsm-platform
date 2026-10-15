package hs.kr.entrydsm.application.adapterin.web.dto.request

import tools.jackson.core.JsonParser
import tools.jackson.core.JsonToken
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.ValueDeserializer

/** 선택 점수의 빈 문자열이 null로 강제 변환되는 것을 막는다. */
class GedTechnologyScoreDeserializer : ValueDeserializer<Int?>() {
    override fun deserialize(parser: JsonParser, context: DeserializationContext): Int? =
        if (parser.currentToken() == JsonToken.VALUE_NUMBER_INT) parser.intValue
        else context.handleUnexpectedToken(Int::class.javaObjectType, parser) as? Int
}
