package io.github.yermilov.kerivnyk.service

import io.github.yermilov.kerivnyk.domain.Job
import io.github.yermilov.kerivnyk.domain.JobLog
import groovy.util.logging.Slf4j
import io.github.yermilov.kerivnyk.domain.JobStatus
import io.github.yermilov.kerivnyk.repository.JobRepository
import org.joda.time.Period
import org.joda.time.format.PeriodFormatter
import org.joda.time.format.PeriodFormatterBuilder
import org.springframework.core.task.TaskExecutor
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service

import javax.annotation.PostConstruct
import java.time.LocalDateTime

@Service
@Slf4j
class KerivnykService {

    static final PeriodFormatter TIME_LIMIT_FORMAT = new PeriodFormatterBuilder()
            .appendHours().appendSuffix('h')
            .appendMinutes().appendSuffix('min')
            .appendSeconds().appendSuffix('sec')
            .toFormatter()

    JobRepository jobRepository

    TaskExecutor taskExecutor

    String executorQualifier

    String updateDelay

    Integer maxMessageLogCount

    @PostConstruct
    void markAbortedJobs() {
        log.info "Marking all aborted jobs for executor [${executorQualifier}]..."
        Collection<Job> activeJobs = findActiveJobs()
        if (activeJobs.empty) {
            log.info "Found no active jobs for executor [${executorQualifier}]"
        } else {
            log.warn "Found ${activeJobs.size()} active jobs for executor [${executorQualifier}] to abort"
            activeJobs.each { it.status = JobStatus.ABORTED.toString() }
            jobRepository.save(activeJobs)
        }
    }

    Job getActiveJob() {
        Collection<Job> activeJobs = findActiveJobs()

        if (activeJobs.empty) {
            return null
        }

        if (activeJobs.size() > 1) {
            log.error("There are more than one active job for executor [${executorQualifier}]: ${activeJobs}")
            throw new IllegalStateException("There are more than one active job for executor [${executorQualifier}]: ${activeJobs}")
        }

        return activeJobs.first()
    }

    Job getJobById(String id) {
        jobRepository.findOne(id)
    }

    Job getLastJob() {
        List<Job> lastJob = jobRepository.findByExecutorQualifier(executorQualifier, new PageRequest(0, 1, new Sort(Sort.Direction.DESC, 'startTimestamp'))).content
        if (lastJob.empty) {
            return null
        } else {
            return lastJob.first()
        }
    }

    List<Job> getAllJobs() {
        jobRepository.findByExecutorQualifier(executorQualifier, new Sort(Sort.Direction.DESC, 'startTimestamp'))
    }

    Job startJob(DurableJob durableJob) {
        runJob(durableJob) { Job job -> taskExecutor.execute(this.&executeDurableJob.curry(job, durableJob)) }
    }

    Job startJobAndWaitForFinish(DurableJob durableJob) {
        runJob(durableJob) { Job job -> executeDurableJob(job, durableJob) }
    }

    Job stopJob(Job job) {
        job.status = JobStatus.STOPPING.toString()
        jobRepository.save(job)
    }

    private Job runJob(DurableJob durableJob, Closure runAction) {
        Job job = new Job()
        job.name = durableJob.name
        job.executorQualifier = executorQualifier
        job.status = JobStatus.STARTING.toString()

        job = jobRepository.save job

        Job activeJob = getActiveJob()
        if (activeJob == null) {
            runAction.call(job)
        } else {
            job.lastMessage = "There is another active job for executor [${executorQualifier}] with id=$activeJob.id, can't accept new one"
            log.warn job.lastMessage
            job.status = JobStatus.ABORTED.toString()
            jobRepository.save job
        }

        return job
    }

    void updateJobStatus(Job job, Map params) {
        job = jobRepository.findOne job.id

        String message = params.message
        String publish = params.publish ?: 'optional'

        log.info "${jobLogPrefix(job.id)} ${message}"

        if ((publish != 'never') && (publish == 'always' || job.messageLog == null || job.messageLog.empty || System.currentTimeMillis() - job.messageLog.timestamp.max() > fromDurationString(updateDelay))) {
            JobLog jobLog = new JobLog()
            jobLog.timestamp = System.currentTimeMillis()
            jobLog.time = LocalDateTime.now().toString()
            jobLog.message = message

            job.lastMessage = message
            job.lastUpdateTime = jobLog.time
            job.timeTaken = toDurationString(jobLog.timestamp - job.startTimestamp)
            job.messageLog = (job.messageLog == null ? [ jobLog ] : job.messageLog + jobLog).sort({ -it.timestamp }).take(maxMessageLogCount)

            jobRepository.save job
        }
    }

    private void executeDurableJob(Job job, DurableJob durableJob) {
        try {
            log.info "${jobLogPrefix(job.id)} starting..."
            job.startTimestamp = System.currentTimeMillis()
            job.startTime = LocalDateTime.now().toString()
            job.lastUpdateTime = job.startTime
            job.timeTaken = '0sec'
            job.status = JobStatus.RUNNING.toString()
            job = jobRepository.save job

            durableJob.initSelf(this.&updateJobStatus.curry(job))

            while (true) {
                log.info "${jobLogPrefix(job.id)} already last ${toDurationString(System.currentTimeMillis() - job.startTimestamp)}"

                job = jobRepository.findOne job.id

                if (durableJob.finished) {
                    job.lastMessage = 'finished successfully'
                    log.info "${jobLogPrefix(job.id)} ${job.lastMessage}"
                    break
                }

                if (job.status == JobStatus.STOPPING.toString()) {
                    job.lastMessage = 'stopped by client request'
                    log.info "${jobLogPrefix(job.id)} ${job.lastMessage}"
                    break
                }

                if (durableJob.timeLimit != null && System.currentTimeMillis() - job.startTimestamp > fromDurationString(durableJob.timeLimit)) {
                    job.lastMessage = 'time limit is exceeded'
                    log.info "${jobLogPrefix(job.id)} ${job.lastMessage}"
                    break
                }

                durableJob.doSomething(this.&updateJobStatus.curry(job))
            }

            log.info "${jobLogPrefix(job.id)} succeeded"
            job = jobRepository.findOne job.id
            job.endTime = LocalDateTime.now().toString()
            job.lastUpdateTime = job.endTime
            job.timeTaken = toDurationString(System.currentTimeMillis() - job.startTimestamp)
            job.status = JobStatus.COMPLETED.toString()
            jobRepository.save job
        } catch (e) {
            log.error("${jobLogPrefix(job.id)} failed", e)
            job = jobRepository.findOne job.id
            job.lastMessage = "Failed because of ${e.class.name}, with lastMessage: ${e.message}"
            job.endTime = LocalDateTime.now().toString()
            job.lastUpdateTime = job.endTime
            job.timeTaken = toDurationString(System.currentTimeMillis() - job.startTimestamp)
            job.status = JobStatus.FAILED.toString()
            jobRepository.save job
        }
    }

    private Collection<Job> findActiveJobs() {
        jobRepository.findByExecutorQualifierAndStatusIn(executorQualifier, [ JobStatus.RUNNING.toString(), JobStatus.STOPPING.toString() ])
    }

    private String jobLogPrefix(Job job) {
        "job=${executorQualifier}:${job.id} (after ${toDurationString(System.currentTimeMillis() - job.startTimestamp)}):"
    }

    static String toDurationString(long duration) {
        String durationString = TIME_LIMIT_FORMAT.print(new Period(duration))
        if (durationString.empty) {
            return '0sec'
        } else {
            return durationString
        }
    }

    static long fromDurationString(String durationAsString) {
        TIME_LIMIT_FORMAT.parsePeriod(durationAsString).toStandardDuration().getStandardSeconds() * 1000
    }
}
