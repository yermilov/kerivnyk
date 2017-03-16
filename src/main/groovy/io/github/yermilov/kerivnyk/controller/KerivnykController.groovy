package io.github.yermilov.kerivnyk.controller

import groovy.util.logging.Slf4j
import io.github.yermilov.kerivnyk.domain.Job
import io.github.yermilov.kerivnyk.service.KerivnykService
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.RestController

@RestController
@Slf4j
class KerivnykController {

    KerivnykService kerivnykService

    @GetMapping(path = '/kerivnyk/job/{jobId}')
    @ResponseBody Job getJobById(@PathVariable String jobId) {
        kerivnykService.getJobById(jobId)
    }

    @GetMapping(path = '/kerivnyk/job/last')
    @ResponseBody Job getLastJob() {
        kerivnykService.getLastJob()
    }

    @GetMapping(path = '/kerivnyk/job/active')
    @ResponseBody Collection<Job> getActiveJobs() {
        kerivnykService.getActiveJobs()
    }

    @GetMapping(path = '/kerivnyk/job')
    @ResponseBody Collection<Job> getAllJobs() {
        kerivnykService.getAllJobs()
    }

    @DeleteMapping(path = '/kerivnyk/job/{jobId}')
    @ResponseBody Job stopJobById(@PathVariable String jobId) {
        log.info "Receive request to stop job with id=${jobId}"

        Job job = kerivnykService.getJobById(jobId)

        if (job == null) {
            return null
        } else {
            return kerivnykService.stopJob(job)
        }
    }
}
