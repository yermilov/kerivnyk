package io.github.yermilov.kerivnyk.service

abstract class DurableJob {

    final String name

    final String timeLimit

    boolean finished

    DurableJob(String name) {
        this(name, null)
    }

    DurableJob(String name, String timeLimit) {
        this.name = name
        this.timeLimit = timeLimit
        this.finished = false
    }

    abstract void initSelf(Closure statusUpdater)

    abstract void doSomething(Closure statusUpdater)

    void markFinished() {
        this.finished = true
    }
}
