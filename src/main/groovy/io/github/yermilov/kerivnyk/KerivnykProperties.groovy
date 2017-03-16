package io.github.yermilov.kerivnyk

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties('kerivnyk')
class KerivnykProperties {

    String executorQualifier = 'default'
}
