package io.github.yermilov.kerivnyk.service

import io.github.yermilov.kerivnyk.domain.Job

import static io.github.yermilov.kerivnyk.util.DurationUtils.fromDurationString

abstract class DurableJob {

    final String name

    final String timeLimit

    boolean finished
    boolean suspended
    long suspendedUntil

    Map<String, Object> storage

    DurableJob(String name) {
        this(name, null)
    }

    DurableJob(String name, String timeLimit) {
        this.name = name
        this.timeLimit = timeLimit
        this.finished = false
        this.storage = [:]
    }

    boolean canStart(boolean isNew, Collection<Job> concurrentJobs) {
        true
    }

    void init() {
        // do nothing by default
    }

    void act() {
        // do nothing by default
    }

    void destroy() {
        // do nothing by default
    }

    final void suspend(String suspendDuration) {
        this.suspended = true
        this.suspendedUntil = System.currentTimeMillis() + fromDurationString(suspendDuration)
    }

    final boolean canResume() {
        if (System.currentTimeMillis() > suspendedUntil) (
            suspended = false
        )
        return !suspended
    }

    final void finished() {
        this.finished = true
    }
}
