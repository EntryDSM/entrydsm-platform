package hs.kr.entrydsm.admin.adapterin.web

import hs.kr.entrydsm.admin.adapterin.web.exception.GlobalExceptionHandler
import hs.kr.entrydsm.admin.domain.port.`in`.ApplicationCorrectionResult
import hs.kr.entrydsm.admin.domain.port.`in`.CorrectApplicationUseCase
import org.junit.Assert.*
import org.junit.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class ApplicationCorrectionControllerTest {
    private fun <T> proxy(type: Class<T>, invoke: (String, Array<out Any?>) -> Any? = { name, _ -> error("unexpected call: $name") }): T = type.cast(java.lang.reflect.Proxy.newProxyInstance(javaClass.classLoader, arrayOf(type)) { _, method, args -> invoke(method.name, args ?: emptyArray()) })
    @Test fun individualApplicantChangesRequireAdministratorAndOnlyPassTargetId() {
        val calls = mutableListOf<Pair<String, Long>>()
        val screening = proxy(hs.kr.entrydsm.admin.domain.port.`in`.EvaluateFirstScreeningUseCase::class.java) { name, args -> calls.add(name to (args[0] as Long)); Unit }
        val controller = ScreeningController(
            proxy(hs.kr.entrydsm.admin.domain.port.`in`.ReadScorePolicyUseCase::class.java),
            proxy(hs.kr.entrydsm.admin.domain.port.`in`.UpdateScorePolicyUseCase::class.java),
            proxy(hs.kr.entrydsm.admin.domain.port.`in`.ReadAdmissionQuotaUseCase::class.java),
            proxy(hs.kr.entrydsm.admin.domain.port.`in`.UpdateAdmissionQuotaUseCase::class.java), screening,
            proxy(hs.kr.entrydsm.admin.domain.port.`in`.EvaluateFinalScreeningUseCase::class.java),
            proxy(hs.kr.entrydsm.admin.domain.port.`in`.ReadStatisticsUseCase::class.java))
        val mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(GlobalExceptionHandler())
            .addInterceptors(AdminAuthorizationInterceptor()).build()
        val path = "/api/v11/admin/screenings/first/applicants/5"
        for (request in listOf(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path),
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(path))) {
            assertEquals(401, mvc.perform(request).andReturn().response.status)
        }
        assertEquals(403, mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path)
            .header("X-User-Id", "10").header("X-User-Role", "USER")).andReturn().response.status)
        assertTrue(calls.isEmpty())
        assertEquals(204, mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path)
            .header("X-User-Id", "10").header("X-User-Role", "ADMIN")).andReturn().response.status)
        assertEquals(204, mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(path)
            .header("X-User-Id", "10").header("X-User-Role", "ADMIN")).andReturn().response.status)
        assertEquals(listOf("selectFirst" to 5L, "cancelFirst" to 5L), calls)
    }

    @Test fun editableFieldsRequiresAdministratorAndReturnsUseCaseFields() {
        val useCase = object : CorrectApplicationUseCase {
            override fun correct(command: hs.kr.entrydsm.admin.domain.command.CorrectApplicationCommand, editorId: String): ApplicationCorrectionResult =
                error("GET 요청이 원서를 수정하면 안 된다")
            override fun editableFields(applicantId: Long): hs.kr.entrydsm.admin.domain.port.`in`.EditableApplicationFields {
                assertEquals(5L, applicantId)
                return hs.kr.entrydsm.admin.domain.port.`in`.EditableApplicationFields(hs.kr.entrydsm.admin.domain.command.ApplicationFormChanges(name = "홍길동", admissionType = "GENERAL"), null)
            }
        }
        val mvc = MockMvcBuilders.standaloneSetup(ApplicationCorrectionController(useCase))
            .setControllerAdvice(GlobalExceptionHandler()).addInterceptors(AdminAuthorizationInterceptor()).build()
        val path = "/api/v11/admin/applicants/5/application"
        assertEquals(401, mvc.perform(get(path)).andReturn().response.status)
        assertEquals(403, mvc.perform(get(path).header("X-User-Id", "10").header("X-User-Role", "USER")).andReturn().response.status)
        val response = mvc.perform(get(path).header("X-User-Id", "10").header("X-User-Role", "ADMIN")).andReturn().response
        assertEquals(200, response.status)
        assertFalse(response.contentAsString.contains("\"editable\""))
        assertFalse(response.contentAsString.contains("\"values\""))
        assertTrue(response.contentAsString.contains("홍길동"))
        assertTrue(response.contentAsString.contains("\"name\""))
        assertEquals(404, mvc.perform(get("$path/editable-fields").header("X-User-Id", "10")
            .header("X-User-Role", "ADMIN")).andReturn().response.status)
    }

    @Test fun currentFieldValuesRoundTripFromGrpcThroughRealBffToHttpForEditableAndReadOnlyApplicants() {
        val mapper = tools.jackson.databind.json.JsonMapper.builder().addModule(tools.jackson.module.kotlin.KotlinModule.Builder().build()).build()
        val fieldNames = setOf("admissionType", "region", "graduationType", "graduationDate", "photoFileId", "name", "phoneNumber",
            "gender", "birthdate", "specialAdmissionType", "guardianName", "guardianPhoneNumber", "guardianGender", "guardianRelation",
            "zipCode", "addressBase", "addressDetail", "introduction", "studyPlan", "school", "academicRecord")
        val schoolFields = """{"admissionType":"GENERAL","region":"NATIONWIDE","graduationType":"PROSPECTIVE","graduationDate":"2027-02","photoFileId":"photo","name":"홍길동","phoneNumber":null,"gender":"MALE","birthdate":"2010-03-02","specialAdmissionType":"NONE","guardianName":"보호자","guardianPhoneNumber":"010-9876-5432","guardianGender":"FEMALE","guardianRelation":"모","zipCode":"34503","addressBase":"대전","addressDetail":"101호","introduction":"자기소개","studyPlan":"학업계획","school":{"schoolCode":"D100","schoolName":"중학교","studentNumber":"30101","schoolPhone":"042-123-4567","teacherName":"담임"},"academicRecord":{"absentCount":1,"lateCount":2,"earlyLeaveCount":3,"classAbsenceCount":4,"volunteerTime":15,"dsmAlgorithmAwarded":true,"programmingCertified":false,"subjectGrades":{"THIRD_GRADE_FIRST_SEMESTER":{"korean":"A","math":"B","english":"C","science":"D","society":"E","technology":"X","history":"A"}},"gedScores":null}}"""
        val schoolNode = mapper.readTree(schoolFields)
        val gedNode = schoolNode.deepCopy<tools.jackson.databind.node.ObjectNode>().apply {
            put("graduationType", "GED"); putNull("graduationDate"); putNull("school")
            set<tools.jackson.databind.JsonNode>("academicRecord", mapper.readTree("""{"absentCount":0,"lateCount":0,"earlyLeaveCount":0,"classAbsenceCount":0,"volunteerTime":0,"dsmAlgorithmAwarded":false,"programmingCertified":false,"subjectGrades":{},"gedScores":{"korean":100,"math":90,"english":80,"science":70,"society":60,"technology":0,"history":40}}"""))
        }
        var payload = ""
        val requests = mutableListOf<Long>()
        val upstream = object : hs.kr.entrydsm.application.grpc.AdministrationServiceGrpc.AdministrationServiceImplBase() {
            override fun readEditableApplicationFields(request: hs.kr.entrydsm.application.grpc.AdministrationRequest,
                observer: io.grpc.stub.StreamObserver<hs.kr.entrydsm.application.grpc.AdministrationResponse>) {
                assertEquals("10", request.userId)
                assertEquals("ADMIN", request.userRole)
                val id = mapper.readTree(request.commandJson).get("applicantId").longValue()
                requests.add(id)
                val response = hs.kr.entrydsm.application.grpc.AdministrationResponse.newBuilder()
                if (id <= 0) response.setErrorCode("INVALID_REQUEST_BODY")
                else if (id == 404L) response.setErrorCode("APPLICANT_NOT_FOUND")
                else response.setDataJson(payload)
                observer.onNext(response.build()); observer.onCompleted()
            }
        }
        val server = io.grpc.ServerBuilder.forPort(0).addService(upstream).build().start()
        val channel = hs.kr.entrydsm.admin.adapterout.grpc.ApplicationGrpcChannel("localhost", server.port, 30000, 30000)
        try {
            val adapter = hs.kr.entrydsm.admin.adapterout.grpc.GrpcAdministrationAdapter(channel)
            val mvc = MockMvcBuilders.standaloneSetup(ApplicationCorrectionController(adapter))
                .setControllerAdvice(GlobalExceptionHandler()).addInterceptors(AdminAuthorizationInterceptor()).build()
            val path = "/api/v11/admin/applicants/5/application"
            assertEquals(401, mvc.perform(get(path)).andReturn().response.status)
            assertEquals(403, mvc.perform(get(path).header("X-User-Id", "10").header("X-User-Role", "USER")).andReturn().response.status)
            assertTrue(requests.isEmpty())
            for (fields in listOf(schoolNode, gedNode)) {
                for (reason in listOf(null, "제출 또는 접수된 원서이며 전형 결과가 없어야 수정할 수 있습니다.")) {
                    payload = """{"fields":$fields,"reason":${mapper.writeValueAsString(reason)}}"""
                    val response = mvc.perform(get(path).header("X-User-Id", "10").header("X-User-Role", "ADMIN")).andReturn().response
                    assertEquals(response.contentAsString, 200, response.status)
                    val data = mapper.readTree(response.getContentAsString(Charsets.UTF_8)).get("data")
                    assertEquals(mapper.readTree(payload), data)
                    assertEquals(setOf("fields", "reason"), data.propertyNames().toSet())
                    assertEquals(fieldNames, data.get("fields").propertyNames().toSet())
                    assertFalse(data.has("editable")); assertFalse(data.has("values"))
                }
            }
            assertEquals(400, mvc.perform(get("/api/v11/admin/applicants/0/application").header("X-User-Id", "10").header("X-User-Role", "ADMIN")).andReturn().response.status)
            assertEquals(404, mvc.perform(get("/api/v11/admin/applicants/404/application").header("X-User-Id", "10").header("X-User-Role", "ADMIN")).andReturn().response.status)
        } finally {
            channel.destroy(); server.shutdownNow().awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)
            org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes()
        }
    }

    @Test fun permissionIsRequiredAndRequestDoesNotNeedVersion() {
        var calls = 0
        val mvc = MockMvcBuilders.standaloneSetup(ApplicationCorrectionController(CorrectApplicationUseCase { command, _ ->
            calls++
            assertEquals(5L, command.applicantId)
            assertEquals("MEISTER", command.changes.admissionType)
            ApplicationCorrectionResult(command.applicantId, 4L)
        })).setControllerAdvice(GlobalExceptionHandler()).addInterceptors(AdminAuthorizationInterceptor()).build()
        val json = """{"reason":"입력 오류 정정","changes":{"admissionType":"MEISTER"}}"""
        val path = "/api/v11/admin/applicants/5/application"
        assertEquals(401, mvc.perform(patch(path).contentType(MediaType.APPLICATION_JSON).content(json)).andReturn().response.status)
        assertEquals(403, mvc.perform(patch(path).header("X-User-Id", "10").header("X-User-Role", "USER")
            .contentType(MediaType.APPLICATION_JSON).content(json)).andReturn().response.status)
        assertEquals(0, calls)
        val result = mvc.perform(patch(path).header("X-User-Id", "10").header("X-User-Role", "ADMIN")
            .contentType(MediaType.APPLICATION_JSON).content(json)).andReturn()
        val response = result.response
        assertEquals(result.resolvedException?.toString(), 200, response.status)
        assertTrue(response.contentAsString.contains("\"version\":4"))
        assertEquals(1, calls)
    }

    @Test fun correctionReasonValidationIsPreservedAfterRequestMove() {
        var calls = 0
        val mvc = MockMvcBuilders.standaloneSetup(ApplicationCorrectionController(CorrectApplicationUseCase { command, _ ->
            calls++
            ApplicationCorrectionResult(command.applicantId, 4L)
        })).setControllerAdvice(GlobalExceptionHandler()).addInterceptors(AdminAuthorizationInterceptor()).build()
        val path = "/api/v11/admin/applicants/5/application"
        for ((reason, expectedStatus) in listOf("" to 400, "   " to 400, "가".repeat(501) to 400, "가".repeat(500) to 200)) {
            val json = """{"reason":"$reason","changes":{"admissionType":"MEISTER"}}"""
            assertEquals(expectedStatus, mvc.perform(patch(path).header("X-User-Id", "10").header("X-User-Role", "ADMIN")
                .contentType(MediaType.APPLICATION_JSON).content(json)).andReturn().response.status)
        }
        assertEquals(1, calls)
    }
}
