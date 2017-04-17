package io.github.yermilov.kerivnyk.domain

import org.springframework.data.annotation.Id

class Job {

    @Id
    String id

    String name

    String executorQualifier

    Long startTimestamp
    String startTime

    String lastUpdateTime

    String endTime
    String timeTaken

    String status
    String message

    Map<String, Object> storage
}
