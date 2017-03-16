package com.github.vuzoll.tasks.controller

import com.github.vuzoll.tasks.domain.Job
import com.github.vuzoll.tasks.service.JobsService
import groovy.util.logging.Slf4j
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.RestController

@RestController
@Slf4j
class JobsController {

    JobsService jobsService

    @GetMapping(path = '/job/{jobId}')
    @ResponseBody Job getJobById(@PathVariable String jobId) {
        jobsService.getJobById(jobId)
    }

    @GetMapping(path = '/job/last')
    @ResponseBody Job getLastJob() {
        jobsService.getLastJob()
    }

    @GetMapping(path = '/job/active')
    @ResponseBody Job getActiveJob() {
        jobsService.getActiveJob()
    }

    @GetMapping(path = '/job')
    @ResponseBody List<Job> getAllJobs() {
        jobsService.getAllJobs()
    }

    @DeleteMapping(path = '/job/{jobId}')
    @ResponseBody Job stopJobById(@PathVariable String jobId) {
        log.info "Receive request to stop job with id=${jobId}"

        Job job = jobsService.getJobById(jobId)

        if (job == null) {
            return null
        } else {
            return jobsService.stopJob(job)
        }
    }

    @DeleteMapping(path = '/job/active')
    @ResponseBody Job stopActiveJob() {
        log.info 'Receive request to stop active job'

        Job job = jobsService.getActiveJob()

        if (job == null) {
            return null
        } else {
            return jobsService.stopJob(job)
        }
    }
}
