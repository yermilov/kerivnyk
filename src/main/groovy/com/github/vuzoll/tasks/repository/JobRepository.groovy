package com.github.vuzoll.tasks.repository

import com.github.vuzoll.tasks.domain.Job
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.repository.PagingAndSortingRepository

interface JobRepository extends PagingAndSortingRepository<Job, String> {

    Collection<Job> findByExecutorQualifierAndStatusIn(String executorQualifier, Collection<String> statuses)

    Page<Job> findByExecutorQualifier(String executorQualifier, Pageable pageable)

    Collection<Job> findByExecutorQualifier(String executorQualifier, Sort sort)
}