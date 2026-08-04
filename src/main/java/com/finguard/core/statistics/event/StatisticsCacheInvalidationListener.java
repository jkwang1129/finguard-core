package com.finguard.core.statistics.event;

import com.finguard.core.statistics.cache.StatisticsOverviewCache;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class StatisticsCacheInvalidationListener {

    private final StatisticsOverviewCache cache;

    public StatisticsCacheInvalidationListener(
            StatisticsOverviewCache cache) {
        this.cache = cache;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStatisticsChanged(StatisticsChangedEvent event) {
        cache.evict();
    }
}
