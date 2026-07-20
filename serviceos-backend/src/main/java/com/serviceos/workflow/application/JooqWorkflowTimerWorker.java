package com.serviceos.workflow.application;

import com.serviceos.jooq.generated.tables.WflTimerSubscription;
import com.serviceos.workflow.api.WorkflowTimerFireService;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.serviceos.jooq.generated.tables.WflTimerSubscription.WFL_TIMER_SUBSCRIPTION;

/**
 * TIMER 到期认领 worker（jOOQ 实现）：短事务 claim，再调用流程推进；租约过期可重认领。
 */
@Component
public final class JooqWorkflowTimerWorker {
    public enum RunResult { EMPTY, FIRED, CLAIMED_NONE }

    private final DSLContext dsl;
    private final TransactionTemplate transactions;
    private final WorkflowTimerFireService timerFire;
    private final Clock clock;
    private final String workerId;
    private final Duration lease;

    public JooqWorkflowTimerWorker(
            DSLContext dsl,
            TransactionTemplate transactions,
            WorkflowTimerFireService timerFire,
            Clock clock,
            @Value("${serviceos.workflow.timer.worker-id:workflow-timer-worker}") String workerId,
            @Value("${serviceos.workflow.timer.lease:PT30S}") Duration lease
    ) {
        this.dsl = dsl;
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
        WflTimerSubscription timer = WFL_TIMER_SUBSCRIPTION;
        // FOR UPDATE SKIP LOCKED 让并发 worker 互相跳过已锁行；timestamptz 列直接绑定 Instant。
        List<UUID> due = dsl.select(timer.TIMER_SUBSCRIPTION_ID)
                .from(timer)
                .where(timer.STATUS.eq("WAITING").and(timer.FIRE_AT.le(now))
                        .or(timer.STATUS.eq("CLAIMED").and(timer.CLAIM_UNTIL.lt(now))))
                .orderBy(timer.FIRE_AT, timer.TIMER_SUBSCRIPTION_ID)
                .limit(1)
                .forUpdate()
                .skipLocked()
                .fetch(timer.TIMER_SUBSCRIPTION_ID);
        if (due.isEmpty()) {
            return null;
        }
        UUID timerId = due.getFirst();
        // 认领更新必须带原状态条件（到期 WAITING 或租约过期 CLAIMED），影响行数不为 1 即被其他 worker 抢走。
        int updated = dsl.update(timer)
                .set(timer.STATUS, "CLAIMED")
                .set(timer.CLAIM_OWNER, workerId)
                .set(timer.CLAIM_UNTIL, claimUntil)
                .set(timer.VERSION, timer.VERSION.plus(1))
                .where(timer.TIMER_SUBSCRIPTION_ID.eq(timerId))
                .and(timer.STATUS.eq("WAITING").and(timer.FIRE_AT.le(now))
                        .or(timer.STATUS.eq("CLAIMED").and(timer.CLAIM_UNTIL.lt(now))))
                .execute();
        return updated == 1 ? timerId : null;
    }
}
