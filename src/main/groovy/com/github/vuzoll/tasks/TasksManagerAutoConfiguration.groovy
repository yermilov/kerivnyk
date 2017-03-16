package com.github.vuzoll.tasks

import com.github.vuzoll.tasks.controller.JobsController
import com.github.vuzoll.tasks.repository.JobRepository
import com.github.vuzoll.tasks.service.JobsService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.AutoConfigureAfter
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration
import org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.task.SimpleAsyncTaskExecutor
import org.springframework.core.task.TaskExecutor
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories

@Configuration
@AutoConfigureAfter([ MongoAutoConfiguration, MongoDataAutoConfiguration, MongoRepositoriesAutoConfiguration ])
@EnableConfigurationProperties(TasksManagerProperties)
@EnableMongoRepositories('com.github.vuzoll.tasks.repository')
class TasksManagerAutoConfiguration {

    @Autowired
    TasksManagerProperties tasksManagerProperties

    @Bean
    JobsController jobsController(@Autowired JobsService jobsService) {
        new JobsController(jobsService: jobsService)
    }

    @Bean
    JobsService jobsService(@Autowired JobRepository jobRepository, @Autowired @Qualifier('vuzollTasksExecutor') TaskExecutor taskExecutor) {
        new JobsService(
                jobRepository: jobRepository,
                taskExecutor: taskExecutor,
                executorQualifier: tasksManagerProperties.executorQualifier,
                updateDelay: tasksManagerProperties.updateDelay,
                maxMessageLogCount: tasksManagerProperties.maxMessageLogCount
        )
    }

    @ConditionalOnMissingBean(annotation = TaskExecutorBean)
    @TaskExecutorBean
    TaskExecutor taskExecutor() {
        new SimpleAsyncTaskExecutor()
    }
}
