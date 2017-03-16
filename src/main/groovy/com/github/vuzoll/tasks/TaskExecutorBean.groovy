package com.github.vuzoll.tasks

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Bean
@Qualifier('vuzollTasksExecutor')
@interface TaskExecutorBean {
}
