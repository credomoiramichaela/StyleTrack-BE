package com.styletrack.config;

import com.styletrack.entity.enums.OnlinePaymentStatus;
import com.styletrack.entity.enums.Role;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Hibernate's {@code ddl-auto: update} adds new columns but never changes an existing CHECK constraint. When a
 * value is added to an enum such as {@link OnlinePaymentStatus} or {@link Role}, a database created earlier keeps
 * rejecting it. This rebuilds each listed enum constraint on every start (PostgreSQL only; a fresh or in-memory
 * database already has it right).
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class StatusConstraintSync implements ApplicationRunner {

    private record EnumConstraint(String table, String column, Class<? extends Enum<?>> type) {
        String name() {
            return table + "_" + column + "_check";
        }
    }

    private static final List<EnumConstraint> CONSTRAINTS = List.of(
            new EnumConstraint("online_payments", "status", OnlinePaymentStatus.class),
            new EnumConstraint("users", "role", Role.class));

    private final JdbcTemplate jdbc;

    @Override
    public void run(ApplicationArguments args) {
        try {
            String product = jdbc.execute((java.sql.Connection c) -> c.getMetaData().getDatabaseProductName());
            if (product == null || !product.toLowerCase().contains("postgres")) {
                return;
            }
        } catch (Exception e) {
            log.warn("Could not detect the database product: {}", e.getMessage());
            return;
        }
        migrateRenamedRoles();
        CONSTRAINTS.forEach(this::sync);
    }

    /**
     * One-time data migration for the Role rename (ADMIN -> SUPER_ADMIN, STAFF -> ADMIN). The existing
     * {@code users_role_check} constraint (from before this rename) only allows the old value set, so it must be
     * dropped first or the UPDATE to 'SUPER_ADMIN' would itself violate it; {@link #sync} adds the new constraint
     * back afterward once the data matches it. Order matters: SUPER_ADMIN first, so the second statement doesn't
     * re-touch rows just renamed by the first. Naturally idempotent — once migrated, no row matches the old values
     * again.
     */
    private void migrateRenamedRoles() {
        try {
            jdbc.execute("ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check");
            int toSuperAdmin = jdbc.update("UPDATE users SET role = 'SUPER_ADMIN' WHERE role = 'ADMIN'");
            int toAdmin = jdbc.update("UPDATE users SET role = 'ADMIN' WHERE role = 'STAFF'");
            if (toSuperAdmin > 0 || toAdmin > 0) {
                log.info("Migrated roles: {} user(s) ADMIN->SUPER_ADMIN, {} user(s) STAFF->ADMIN", toSuperAdmin, toAdmin);
            }
        } catch (Exception e) {
            log.warn("Could not migrate renamed roles: {}", e.getMessage());
        }
    }

    private void sync(EnumConstraint c) {
        try {
            String allowed = Arrays.stream(c.type().getEnumConstants())
                    .map(v -> "'" + v.name() + "'")
                    .collect(Collectors.joining(", "));
            jdbc.execute("ALTER TABLE " + c.table() + " DROP CONSTRAINT IF EXISTS " + c.name());
            jdbc.execute("ALTER TABLE " + c.table() + " ADD CONSTRAINT " + c.name()
                    + " CHECK (" + c.column() + " IN (" + allowed + "))");
            log.info("Constraint {} synced: {}", c.name(), allowed);
        } catch (Exception e) {
            // never block startup over this: the worst case is the old constraint stays in place
            log.warn("Could not sync constraint {}: {}", c.name(), e.getMessage());
        }
    }
}
