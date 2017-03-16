package io.github.yermilov.kerivnyk.service

import io.github.yermilov.kerivnyk.domain.Job

abstract class DurableJob {

    final String name

    final String timeLimit

    boolean finished

    Map<String, Object> dashboard

    DurableJob(String name) {
        this(name, null)
    }

    DurableJob(String name, String timeLimit) {
        this.name = name
        this.timeLimit = timeLimit
        this.finished = false
        this.dashboard = [:]
    }

    boolean canStart(Collection<Job> concurrentJobs) {
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

    final void finished() {
        this.finished = true
    }
}
