package com.finguard.core.risk.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "finguard.risk")
public class RiskProperties {

    @Valid
    private final LargeAmount largeAmount = new LargeAmount();

    @Valid
    private final PossibleDuplicate possibleDuplicate =
            new PossibleDuplicate();

    @Valid
    private final FrequentTransaction frequentTransaction =
            new FrequentTransaction();

    public LargeAmount getLargeAmount() {
        return largeAmount;
    }

    public PossibleDuplicate getPossibleDuplicate() {
        return possibleDuplicate;
    }

    public FrequentTransaction getFrequentTransaction() {
        return frequentTransaction;
    }

    public static class LargeAmount {

        private boolean enabled = true;

        @NotNull
        @DecimalMin(value = "0.01")
        @Digits(integer = 17, fraction = 2)
        private BigDecimal threshold = new BigDecimal("10000.00");

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public BigDecimal getThreshold() {
            return threshold;
        }

        public void setThreshold(BigDecimal threshold) {
            this.threshold = threshold;
        }
    }

    public static class PossibleDuplicate {

        private boolean enabled = true;

        @NotNull
        private Duration window = Duration.ofMinutes(5);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Duration getWindow() {
            return window;
        }

        public void setWindow(Duration window) {
            this.window = window;
        }

        @AssertTrue(message = "window must be a positive whole number of seconds")
        public boolean isWindowValid() {
            return validWholeSeconds(window);
        }

        public int windowSeconds() {
            return toSeconds(window);
        }
    }

    public static class FrequentTransaction {

        private boolean enabled = true;

        @NotNull
        private Duration window = Duration.ofMinutes(10);

        @Min(1)
        private int thresholdCount = 5;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Duration getWindow() {
            return window;
        }

        public void setWindow(Duration window) {
            this.window = window;
        }

        public int getThresholdCount() {
            return thresholdCount;
        }

        public void setThresholdCount(int thresholdCount) {
            this.thresholdCount = thresholdCount;
        }

        @AssertTrue(message = "window must be a positive whole number of seconds")
        public boolean isWindowValid() {
            return validWholeSeconds(window);
        }

        public int windowSeconds() {
            return toSeconds(window);
        }
    }

    private static boolean validWholeSeconds(Duration duration) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            return false;
        }
        long seconds = duration.getSeconds();
        return duration.getNano() == 0
                && seconds > 0
                && seconds <= Integer.MAX_VALUE;
    }

    private static int toSeconds(Duration duration) {
        if (!validWholeSeconds(duration)) {
            throw new IllegalStateException(
                    "Risk window must be a positive whole number of seconds"
            );
        }
        return Math.toIntExact(duration.getSeconds());
    }
}
