package com.tradepulse.stockservice.health;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

@Component("hikariPool")
public class HikariPoolHealthIndicator implements HealthIndicator {

    private final DataSource dataSource;

    public HikariPoolHealthIndicator(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Health health() {
        if (!(dataSource instanceof HikariDataSource hikariDataSource)) {
            return Health.unknown().withDetail("reason", "DataSource is not HikariDataSource").build();
        }

        HikariPoolMXBean mxBean = hikariDataSource.getHikariPoolMXBean();
        if (mxBean == null) {
            return Health.unknown().withDetail("reason", "Hikari pool MXBean unavailable").build();
        }

        int active = mxBean.getActiveConnections();
        int idle = mxBean.getIdleConnections();
        int total = mxBean.getTotalConnections();
        int awaiting = mxBean.getThreadsAwaitingConnection();
        int maxPoolSize = hikariDataSource.getMaximumPoolSize();
        double saturationPct = maxPoolSize > 0 ? (active * 100.0) / maxPoolSize : 0.0;

        Health.Builder builder = (awaiting > 0 && active >= maxPoolSize) ? Health.down() : Health.up();
        if (saturationPct >= 85.0 || awaiting > 0) {
            builder.withDetail("warning", "Connection pool is under pressure");
        }

        return builder
                .withDetail("poolName", hikariDataSource.getPoolName())
                .withDetail("activeConnections", active)
                .withDetail("idleConnections", idle)
                .withDetail("totalConnections", total)
                .withDetail("threadsAwaitingConnection", awaiting)
                .withDetail("maximumPoolSize", maxPoolSize)
                .withDetail("saturationPct", Math.round(saturationPct * 100.0) / 100.0)
                .build();
    }
}


