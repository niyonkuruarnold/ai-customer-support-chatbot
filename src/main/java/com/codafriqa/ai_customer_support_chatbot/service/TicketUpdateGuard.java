package com.codafriqa.ai_customer_support_chatbot.service;

import com.codafriqa.ai_customer_support_chatbot.exception.ResourceNotFoundException;
import com.codafriqa.ai_customer_support_chatbot.model.SupportTicket;
import com.codafriqa.ai_customer_support_chatbot.repository.SupportTicketRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PersistenceContext;
import org.hibernate.StaleStateException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Safely applies concurrent updates to {@link SupportTicket} rows.
 *
 * <p>The chat pipeline (escalation during {@code ChatService.sendMessage}) and
 * the agent workspace (reply / internal note / takeover / resolve) can both
 * write the same ticket row at the same time. Ticket mutations were previously
 * performed on detached entities loaded outside the writing transaction, which
 * produced stale-state conflicts
 * ({@code ObjectOptimisticLockingFailureException} caused by
 * {@code StaleObjectStateException}) and silent lost updates.
 *
 * <p>This guard fixes that by running every mutation inside a dedicated
 * transaction that <b>reloads the latest row state first</b>, and by retrying
 * the whole load-mutate-commit cycle a few times (with a short backoff) when a
 * concurrent-update conflict is detected. Each attempt uses a new transaction,
 * so a failed attempt is fully rolled back before the retry starts.
 */
@Component
public class TicketUpdateGuard {

    private static final Logger log = LoggerFactory.getLogger(TicketUpdateGuard.class);

    /** Maximum load-mutate-commit attempts before giving up. */
    private static final int MAX_ATTEMPTS = 3;

    /** Backoff between retry attempts. */
    private static final long RETRY_BACKOFF_MS = 50;

    private final SupportTicketRepository ticketRepository;
    private final TransactionTemplate transactionTemplate;

    /**
     * Request-scoped persistence context (open-in-view). Retries must evict
     * it first: otherwise {@code findById} returns the same stale, already-
     * mutated instance from the L1 cache and every retry would conflict again
     * with an out-of-date version.
     */
    @PersistenceContext
    private EntityManager entityManager;

    public TicketUpdateGuard(SupportTicketRepository ticketRepository,
                             PlatformTransactionManager transactionManager) {
        this.ticketRepository = ticketRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        // Each attempt must own its transaction: a failed attempt is rolled
        // back and the retry starts from a clean persistence context.
        this.transactionTemplate.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Reload the latest state of the ticket, apply {@code mutation}, and commit —
     * retrying on concurrent-update conflicts.
     *
     * @param ticketId primary key of the ticket to mutate
     * @param mutation mutation applied to the freshly-loaded managed entity
     * @return the mutated (committed) entity
     * @throws ResourceNotFoundException if the ticket no longer exists
     * @throws RuntimeException the last concurrency failure if all attempts fail
     */
    public SupportTicket mutate(Long ticketId, Consumer<SupportTicket> mutation) {
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                // Evict any stale copy from the request-scoped L1 cache so the
                // reload below always reads the latest row + version from the DB.
                entityManager.clear();
                SupportTicket mutated = transactionTemplate.execute(status -> {
                    SupportTicket latest = ticketRepository.findById(ticketId)
                            .orElseThrow(() -> new ResourceNotFoundException(
                                    "Support ticket not found: " + ticketId));
                    mutation.accept(latest);
                    // Managed entity: dirty checking flushes the change on commit.
                    return latest;
                });
                if (attempt > 1) {
                    log.info("Support ticket {} update succeeded on attempt {}/{}",
                            ticketId, attempt, MAX_ATTEMPTS);
                }
                return mutated;
            } catch (ConcurrencyFailureException | OptimisticLockException
                    | StaleStateException e) {
                lastFailure = e;
                log.warn("Concurrent update detected on support ticket {} (attempt {}/{}): {}",
                        ticketId, attempt, MAX_ATTEMPTS, e.getMessage());
                backoff(attempt);
            }
        }
        log.error("Giving up updating support ticket {} after {} attempts",
                ticketId, MAX_ATTEMPTS, lastFailure);
        throw lastFailure;
    }

    /**
     * Run an externally-proxied ticket operation (e.g. a {@code @Transactional}
     * service call), retrying when it fails with a concurrent-update conflict.
     * Each retry re-enters the proxy, so it reloads the latest state in a new
     * transaction.
     *
     * @param operation the proxied operation to run
     * @return the operation result
     */
    public <T> T retry(Supplier<T> operation) {
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                // See mutate(): drop stale L1 state so a retried transactional
                // service call reloads fresh entity state from the database.
                entityManager.clear();
                return operation.get();
            } catch (RuntimeException e) {
                if (!isConcurrentUpdate(e)) {
                    throw e;
                }
                lastFailure = e;
                log.warn("Concurrent ticket update (attempt {}/{}): {}",
                        attempt, MAX_ATTEMPTS, e.getMessage());
                backoff(attempt);
            }
        }
        log.error("Giving up ticket operation after {} attempts", MAX_ATTEMPTS, lastFailure);
        throw lastFailure;
    }

    /**
     * Detects optimistic-lock / stale-state failures anywhere in the cause
     * chain. Handles both raw persistence exceptions (thrown inside a flush)
     * and Spring-translated {@link ConcurrencyFailureException}s surfaced at
     * the transaction boundary.
     */
    public static boolean isConcurrentUpdate(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof ConcurrencyFailureException
                    || current instanceof OptimisticLockException
                    || current instanceof StaleStateException) {
                return true;
            }
            current = current.getCause() == current ? null : current.getCause();
        }
        return false;
    }

    private void backoff(int attempt) {
        if (attempt >= MAX_ATTEMPTS) {
            return;
        }
        try {
            Thread.sleep(RETRY_BACKOFF_MS * attempt);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
