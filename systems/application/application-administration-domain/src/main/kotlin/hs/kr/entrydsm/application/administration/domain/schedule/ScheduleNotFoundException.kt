package hs.kr.entrydsm.application.administration.domain.schedule

class ScheduleNotFoundException(title: String) : RuntimeException("일정을 찾을 수 없습니다: $title")
