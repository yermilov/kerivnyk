package io.github.yermilov.kerivnyk

import io.github.yermilov.kerivnyk.controller.KerivnykController
import io.github.yermilov.kerivnyk.repository.JobRepository
import io.github.yermilov.kerivnyk.service.KerivnykService
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
@EnableConfigurationProperties(KerivnykProperties)
@EnableMongoRepositories('io.github.yermilov.kerivnyk.repository')
class KerivnykAutoConfiguration {

    @Autowired
    KerivnykProperties kerivnykProperties

    @Bean
    KerivnykController kerivnykController(@Autowired KerivnykService kerivnykService) {
        new KerivnykController(kerivnykService: kerivnykService)
    }

    @Bean
    KerivnykService kerivnykService(@Autowired JobRepository jobRepository, @Autowired @Qualifier('KerivnykExecutorBean') TaskExecutor taskExecutor) {
        new KerivnykService(
                jobRepository: jobRepository,
                taskExecutor: taskExecutor,
                executorQualifier: kerivnykProperties.executorQualifier
        )
    }

    @ConditionalOnMissingBean(annotation = KerivnykExecutorBean)
    @KerivnykExecutorBean
    TaskExecutor taskExecutor() {
        new SimpleAsyncTaskExecutor()
    }
}
