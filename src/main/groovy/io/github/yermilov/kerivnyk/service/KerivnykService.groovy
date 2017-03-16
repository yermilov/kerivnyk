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

    Job asyncStartJob(DurableJob durableJob) {
        runJob(durableJob) { Job job -> taskExecutor.execute(this.&executeDurableJob.curry(job, durableJob)) }
    }

    Job syncStartJob(DurableJob durableJob) {
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
            job.message = "There is another active job for executor [${executorQualifier}] with id=$activeJob.id, can't accept new one"
            log.warn job.message
            job.status = JobStatus.ABORTED.toString()
            jobRepository.save job
        }

        return job
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

            durableJob.init()

            while (true) {
                job = jobRepository.findOne job.id
                job.dashboard = durableJob.dashboard
                job.lastUpdateTime = LocalDateTime.now().toString()
                job.timeTaken = toDurationString(job.lastUpdateTime - job.startTimestamp)
                jobRepository.save job

                if (durableJob.finished) {
                    job.message = 'finished successfully'
                    log.info "${jobLogPrefix(job.id)} ${job.message}"
                    break
                }

                if (job.status == JobStatus.STOPPING.toString()) {
                    job.message = 'stopped by client request'
                    log.info "${jobLogPrefix(job.id)} ${job.message}"
                    break
                }

                if (durableJob.timeLimit != null && System.currentTimeMillis() - job.startTimestamp > fromDurationString(durableJob.timeLimit)) {
                    job.message = 'time limit is exceeded'
                    log.info "${jobLogPrefix(job.id)} ${job.message}"
                    break
                }

                durableJob.act()
            }

            durableJob.destroy()

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
            job.message = "Failed because of ${e.class.name}, with message: ${e.message}"
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
}
