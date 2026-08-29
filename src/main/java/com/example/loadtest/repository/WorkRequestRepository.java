package com.example.loadtest.repository;

import com.example.loadtest.model.WorkRequest;
import com.example.loadtest.model.WorkRequestStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface WorkRequestRepository extends JpaRepository<WorkRequest, UUID> {
    Optional<WorkRequest> findByLoadTestId(UUID loadTestId);

    @Query("""
            select workRequest.id from WorkRequest workRequest
            where workRequest.status in :statuses
              and workRequest.nextAttemptAt <= :now
              and (workRequest.leaseUntil is null or workRequest.leaseUntil <= :now)
            order by workRequest.createdAt asc
            """)
    List<UUID> findRunnableIds(
            @Param("statuses") List<WorkRequestStatus> statuses,
            @Param("now") Instant now,
            Pageable pageable);

    /**
     * Claims a request only when it is still runnable. The conditional update is the lease:
     * competing application nodes may both see an id, but only one update can affect one row.
     */
    @Modifying
    @Transactional
    @Query("""
            update WorkRequest workRequest
            set workRequest.leaseOwner = :leaseOwner,
                workRequest.leaseUntil = :leaseUntil,
                workRequest.leaseGeneration = workRequest.leaseGeneration + 1,
                workRequest.updatedAt = :now
            where workRequest.id = :workRequestId
              and workRequest.status in :statuses
              and workRequest.nextAttemptAt <= :now
              and (workRequest.leaseUntil is null or workRequest.leaseUntil <= :now)
            """)
    int tryAcquireLease(
            @Param("workRequestId") UUID workRequestId,
            @Param("statuses") List<WorkRequestStatus> statuses,
            @Param("leaseOwner") String leaseOwner,
            @Param("leaseUntil") Instant leaseUntil,
            @Param("now") Instant now);

    @Modifying
    @Transactional
    @Query("""
            update WorkRequest workRequest
            set workRequest.status = case
                    when workRequest.status = :acceptedStatus then :runningStatus
                    else workRequest.status
                end,
                workRequest.updatedAt = :now
            where workRequest.id = :workRequestId
              and workRequest.leaseOwner = :leaseOwner
              and workRequest.leaseGeneration = :leaseGeneration
              and workRequest.leaseUntil > :now
              and workRequest.status in :runnableStatuses
            """)
    int markAcceptedRunningOrVerifyLeaseOwned(
            @Param("workRequestId") UUID workRequestId,
            @Param("leaseOwner") String leaseOwner,
            @Param("leaseGeneration") long leaseGeneration,
            @Param("acceptedStatus") WorkRequestStatus acceptedStatus,
            @Param("runningStatus") WorkRequestStatus runningStatus,
            @Param("runnableStatuses") List<WorkRequestStatus> runnableStatuses,
            @Param("now") Instant now);

    @Modifying
    @Transactional
    @Query("""
            update WorkRequest workRequest
            set workRequest.leaseUntil = :leaseUntil,
                workRequest.updatedAt = :now
            where workRequest.id = :workRequestId
              and workRequest.leaseOwner = :leaseOwner
              and workRequest.leaseGeneration = :leaseGeneration
              and workRequest.leaseUntil > :now
              and workRequest.status in :statuses
            """)
    int renewLease(
            @Param("workRequestId") UUID workRequestId,
            @Param("leaseOwner") String leaseOwner,
            @Param("leaseGeneration") long leaseGeneration,
            @Param("leaseUntil") Instant leaseUntil,
            @Param("statuses") List<WorkRequestStatus> statuses,
            @Param("now") Instant now);

    @Modifying
    @Transactional
    @Query("""
            update WorkRequest workRequest
            set workRequest.leaseOwner = null,
                workRequest.leaseUntil = null,
                workRequest.updatedAt = :now
            where workRequest.id = :workRequestId
              and workRequest.leaseOwner = :leaseOwner
              and workRequest.leaseGeneration = :leaseGeneration
              and workRequest.status = :status
            """)
    int releaseLease(
            @Param("workRequestId") UUID workRequestId,
            @Param("leaseOwner") String leaseOwner,
            @Param("leaseGeneration") long leaseGeneration,
            @Param("status") WorkRequestStatus status,
            @Param("now") Instant now);
}
