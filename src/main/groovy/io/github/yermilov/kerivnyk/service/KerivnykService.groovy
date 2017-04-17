package io.github.yermilov.kerivnyk.service

import io.github.yermilov.kerivnyk.domain.Job

import groovy.util.logging.Slf4j
import io.github.yermilov.kerivnyk.domain.JobStatus
import io.github.yermilov.kerivnyk.repository.JobRepository
import org.springframework.core.task.TaskExecutor
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service

import javax.annotation.PostConstruct
import java.time.LocalDateTime
import java.time.ZoneId

import static io.github.yermilov.kerivnyk.util.DurationUtils.fromDurationString
import static io.github.yermilov.kerivnyk.util.DurationUtils.toDurationString

@Service
@Slf4j
class KerivnykService {

    static final Sort START_TIMESTAMP_DESC = new Sort(Sort.Direction.DESC, 'startTimestamp')

    static final Pageable LAST_JOB_REQUEST = new PageRequest(0, 1, START_TIMESTAMP_DESC)

    JobRepository jobRepository

    TaskExecutor taskExecutor

    String executorQualifier

    @PostConstruct
    void markAbortedJobs() {
        log.info "Marking all aborted jobs for executor [${executorQualifier}]..."
        Collection<Job> activeJobs = getActiveJobs()
        if (activeJobs.empty) {
            log.info "Found no active jobs for executor [${executorQualifier}]"
        } else {
            log.warn "Found ${activeJobs.size()} active jobs for executor [${executorQualifier}] to abort"
            activeJobs.each { it.status = JobStatus.ABORTED.toString() }
            jobRepository.save(activeJobs)
        }
    }

    Collection<Job> getActiveJobs() {
        jobRepository.findByExecutorQualifierAndStatusIn(executorQualifier, [ JobStatus.STARTING.toString(), JobStatus.RUNNING.toString(), JobStatus.SUSPENDED.toString(), JobStatus.STOPPING.toString() ])
    }

    Job getJobById(String id) {
        jobRepository.findOne(id)
    }

    Job getLastJob() {
        List<Job> lastJob = jobRepository.findByExecutorQualifier(executorQualifier, LAST_JOB_REQUEST).content
        if (lastJob.empty) {
            return null
        } else {
            return lastJob.first()
        }
    }

    List<Job> getAllJobs() {
        jobRepository.findByExecutorQualifier(executorQualifier, START_TIMESTAMP_DESC)
    }

    Job startJob(DurableJob durableJob) {
        runJob(durableJob) { Job job -> taskExecutor.execute(this.&executeDurableJob.curry(job, durableJob)) }
    }

    Job restartJobFrom(DurableJob durableJob, Job resurrectionJob) {
        resurrectJob(durableJob, resurrectionJob) { Job job -> taskExecutor.execute(this.&executeDurableJob.curry(job, durableJob)) }
    }

    Job doJob(DurableJob durableJob) {
        runJob(durableJob) { Job job -> executeDurableJob(job, durableJob) }
    }

    Job continueJobFrom(DurableJob durableJob, Job resurrectionJob) {
        resurrectJob(durableJob, resurrectionJob) { Job job -> executeDurableJob(job, durableJob) }
    }

    Job stopJob(Job job) {
        if (job.status == JobStatus.STARTING.toString()) {
            job.status = JobStatus.ABORTED.toString()
        } else {
            job.status = JobStatus.STOPPING.toString()
        }
        jobRepository.save(job)
    }

    Job runJob(DurableJob durableJob, Closure executeJob) {
        Job job = new Job()
        job.name = durableJob.name
        job.executorQualifier = executorQualifier
        job.status = JobStatus.STARTING.toString()
        job.startTimestamp = System.currentTimeMillis()
        job.startTime = LocalDateTime.now().toString()
        job.timeTaken = '0sec'

        job = jobRepository.save job

        log.info "${jobLogPrefix(job)} checking if it's possible to start..."
        Collection<Job> activeJobs = getActiveJobs().findAll({ it.id != job.id })
        if (durableJob.canStart(true, activeJobs)) {
            log.info "${jobLogPrefix(job)} starting..."
            executeJob(job)
        } else {
            job.message = 'refused to start'
            log.warn "${jobLogPrefix(job)} ${job.message}"
            job.status = JobStatus.ABORTED.toString()
            jobRepository.save job
        }

        return job
    }

    Job resurrectJob(DurableJob durableJob, Job resurrectionJob, Closure executeJob) {
        resurrectionJob.name = durableJob.name
        resurrectionJob.executorQualifier = executorQualifier
        resurrectionJob.status = JobStatus.STARTING.toString()

        resurrectionJob = jobRepository.save resurrectionJob

        log.info "${jobLogPrefix(resurrectionJob)} checking if it's possible to resurrect..."
        Collection<Job> activeJobs = getActiveJobs().findAll({ it.id != resurrectionJob.id })
        if (durableJob.canStart(false, activeJobs)) {
            log.info "${jobLogPrefix(resurrectionJob)} resurrecting..."
            executeJob(resurrectionJob)
        } else {
            resurrectionJob.message = 'refused to resurrect'
            log.warn "${jobLogPrefix(resurrectionJob)} ${resurrectionJob.message}"
            resurrectionJob.status = JobStatus.ABORTED.toString()
            jobRepository.save resurrectionJob
        }

        return resurrectionJob
    }

    void executeDurableJob(Job job, DurableJob durableJob) {
        try {
            if (job.status == JobStatus.ABORTED.toString()) {
                return
            }

            job.lastUpdateTime = LocalDateTime.now().toString()
            job.status = JobStatus.RUNNING.toString()
            job = jobRepository.save job

            log.info "${jobLogPrefix(job)} initializing..."
            durableJob.init()

            while (true) {
                job = jobRepository.findOne job.id
                job.storage = durableJob.storage
                job.lastUpdateTime = LocalDateTime.now().toString()
                job.timeTaken = toDurationString(System.currentTimeMillis() - job.startTimestamp)
                jobRepository.save job

                if (durableJob.finished) {
                    job.message = 'finished successfully'
                    log.info "${jobLogPrefix(job)} ${job.message}"
                    break
                }

                if (job.status == JobStatus.STOPPING.toString() || job.status == JobStatus.ABORTED.toString()) {
                    job.message = 'stopped by client request'
                    log.info "${jobLogPrefix(job)} ${job.message}"
                    break
                }

                if (durableJob.timeLimit != null && System.currentTimeMillis() - job.startTimestamp > fromDurationString(durableJob.timeLimit)) {
                    job.message = 'time limit is exceeded'
                    log.info "${jobLogPrefix(job)} ${job.message}"
                    break
                }

                if (job.status == JobStatus.SUSPENDED.toString()) {
                    if (durableJob.canResume()) {
                        job.status = JobStatus.RUNNING.toString()
                        job.message = null
                        log.info "${jobLogPrefix(job)} resumed"
                        jobRepository.save job
                    } else {
                        continue
                    }
                }

                if (durableJob.suspended) {
                    job.status = JobStatus.SUSPENDED.toString()
                    job.message = "suspended until ${new Date(durableJob.suspendedUntil).toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()}"
                    log.info "${jobLogPrefix(job)} ${job.message}"
                    jobRepository.save job
                    continue
                }

                durableJob.act()
            }

            log.info "${jobLogPrefix(job)} destroing..."
            durableJob.destroy()

            log.info "${jobLogPrefix(job)} succeeded"
            job = jobRepository.findOne job.id
            job.endTime = LocalDateTime.now().toString()
            job.lastUpdateTime = job.endTime
            job.timeTaken = toDurationString(System.currentTimeMillis() - job.startTimestamp)
            job.status = JobStatus.COMPLETED.toString()
            jobRepository.save job
        } catch (e) {
            log.error("${jobLogPrefix(job)} failed", e)
            job = jobRepository.findOne job.id
            job.message = "Failed because of ${e.class.name}, with message: ${e.message}"
            job.endTime = LocalDateTime.now().toString()
            job.lastUpdateTime = job.endTime
            job.timeTaken = toDurationString(System.currentTimeMillis() - job.startTimestamp)
            job.status = JobStatus.FAILED.toString()
            jobRepository.save job
        }
    }

    String jobLogPrefix(Job job) {
        String jobLogPrefix = "job=${executorQualifier}:${job.id}"
        if (job.startTimestamp != null) {
            jobLogPrefix += " (after ${toDurationString(System.currentTimeMillis() - job.startTimestamp)})"
        }
        return jobLogPrefix + ":"
    }
}
