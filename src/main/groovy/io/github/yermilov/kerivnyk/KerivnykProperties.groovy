package io.github.yermilov.kerivnyk

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties('kerivnyk')
class KerivnykProperties {

    String executorQualifier = 'default'

    Integer corePoolSize = 1

    Integer maxPoolSize = Integer.MAX_VALUE

    Integer keepAliveSeconds = 60

    Integer queueCapacity = Integer.MAX_VALUE
}
