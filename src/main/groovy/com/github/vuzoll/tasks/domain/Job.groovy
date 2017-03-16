package com.github.vuzoll.tasks.domain

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
    String lastMessage

    List<JobLog> messageLog
}
