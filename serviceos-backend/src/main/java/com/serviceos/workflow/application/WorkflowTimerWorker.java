package com.serviceos.workflow.application;

import com.serviceos.workflow.api.WorkflowTimerFireService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * TIMER 到期认领 worker：短事务 claim，再调用流程推进；租约过期可重认领。
 */
@Component
public class WorkflowTimerWorker {
    public enum RunResult { EMPTY, FIRED, CLAIMED_NONE }

    private final JdbcClient jdbc;
    private final TransactionTemplate transactions;
    private final WorkflowTimerFireService timerFire;
    private final Clock clock;
    private final String workerId;
    private final Duration lease;

    public WorkflowTimerWorker(
            JdbcClient jdbc,
            TransactionTemplate transactions,
            WorkflowTimerFireService timerFire,
            Clock clock,
            @Value("${serviceos.workflow.timer.worker-id:workflow-timer-worker}") String workerId,
            @Value("${serviceos.workflow.timer.lease:PT30S}") Duration lease
    ) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.timerFire = timerFire;
        this.clock = clock;
        this.workerId = workerId;
        this.lease = lease;
    }

    public RunResult runOnce() {
        UUID timerId = transactions.execute(status -> claimDueTimer());
        if (timerId == null) {
            return RunResult.EMPTY;
        }
        timerFire.fireTimer(timerId, "corr-timer-" + timerId);
        return RunResult.FIRED;
    }

    private UUID claimDueTimer() {
        Instant now = clock.instant();
        Instant claimUntil = now.plus(lease);
        List<UUID> due = jdbc.sql("""
                        SELECT timer_subscription_id
                          FROM wfl_timer_subscription
                         WHERE (status = 'WAITING' AND fire_at <= :now)
                            OR (status = 'CLAIMED' AND claim_until < :now)
                         ORDER BY fire_at, timer_subscription_id
                         LIMIT 1
                         FOR UPDATE SKIP LOCKED
                        """)
                .param("now", java.sql.Timestamp.from(now))
                .query(UUID.class)
                .list();
        if (due.isEmpty()) {
            return null;
        }
        UUID timerId = due.getFirst();
        int updated = jdbc.sql("""
                        UPDATE wfl_timer_subscription
                           SET status = 'CLAIMED',
                               claim_owner = :owner,
                               claim_until = :claimUntil,
                               version = version + 1
                         WHERE timer_subscription_id = :timerId
                           AND (
                                (status = 'WAITING' AND fire_at <= :now)
                             OR (status = 'CLAIMED' AND claim_until < :now)
                           )
                        """)
                .param("owner", workerId)
                .param("claimUntil", java.sql.Timestamp.from(claimUntil))
                .param("timerId", timerId)
                .param("now", java.sql.Timestamp.from(now))
                .update();
        return updated == 1 ? timerId : null;
    }
}
