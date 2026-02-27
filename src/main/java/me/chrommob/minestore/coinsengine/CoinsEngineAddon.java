package me.chrommob.minestore.coinsengine;

import me.chrommob.minestore.api.Registries;
import me.chrommob.minestore.api.generic.MineStoreAddon;
import me.chrommob.minestore.api.interfaces.user.AbstractUser;
import me.chrommob.minestore.api.interfaces.user.CommonUser;
import me.chrommob.minestore.api.scheduler.MineStoreScheduledTask;
import me.chrommob.minestore.coinsengine.provider.CoinsEngineEconomyProvider;
import me.chrommob.minestore.libs.me.chrommob.config.ConfigManager.ConfigKey;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * MineStore addon that backs virtual currency transactions with a CoinsEngine currency.
 * Server administrators configure the CoinsEngine currency ID in the addon config
 * (default: "points"). All MineStore virtual currency balance checks and charges
 * use that currency via CoinsEngine.
 */
@SuppressWarnings("unused")
public class CoinsEngineAddon extends MineStoreAddon {

    private static final ConfigKey<String> CURRENCY_ID = new ConfigKey<>("currency_id", "points");
    private static final ConfigKey<Boolean> BALANCE_MIRROR_ENABLED = new ConfigKey<>("balance_mirror_enabled", true);
    private static final ConfigKey<Integer> BALANCE_MIRROR_INTERVAL_SECONDS = new ConfigKey<>("balance_mirror_interval_seconds", 15);

    private CoinsEngineEconomyProvider economyProvider;
    private MineStoreScheduledTask balanceMirrorTask;
    private boolean loggedMysqlConfigError;

    @Override
    public void onEnable() {
        if (!isCoinsEngineAvailable()) {
            Registries.LOGGER.get().log("[MineStore-CoinsEngine] CoinsEngine is not loaded. Install CoinsEngine and restart the server.");
            return;
        }

        File addonDataFolder = getApiData() != null ? getApiData().getDataFolder() : null;
        File addonConfigFile = addonDataFolder != null ? new File(addonDataFolder, "config") : null;
        Registries.LOGGER.get().log("[MineStore-CoinsEngine] Config source: " + (addonConfigFile != null ? addonConfigFile.getAbsolutePath() : "unavailable"));

        String currencyId = CURRENCY_ID.getValue();
        if (currencyId == null || currencyId.trim().isEmpty()) {
            currencyId = "points";
        }

        economyProvider = new CoinsEngineEconomyProvider(currencyId);
        Registries.PLAYER_ECONOMY_PROVIDER.set(economyProvider);
        startBalanceMirrorWorkaround();
        Registries.LOGGER.get().log("[MineStore-CoinsEngine] Virtual currency is now using CoinsEngine currency: " + currencyId);
    }

    private boolean isCoinsEngineAvailable() {
        try {
            Class<?> api = Class.forName("su.nightexpress.coinsengine.api.CoinsEngineAPI");
            java.lang.reflect.Method m = api.getMethod("isLoaded");
            return Boolean.TRUE.equals(m.invoke(null));
        } catch (Exception ignored) {
            return false;
        }
    }

    @Override
    public String getName() {
        return "MineStore-CoinsEngine";
    }

    @Override
    public List<ConfigKey<?>> getConfigKeys() {
        List<ConfigKey<?>> keys = new ArrayList<>();
        keys.add(CURRENCY_ID);
        keys.add(BALANCE_MIRROR_ENABLED);
        keys.add(BALANCE_MIRROR_INTERVAL_SECONDS);
        return keys;
    }

    private void startBalanceMirrorWorkaround() {
        boolean enabled = Boolean.TRUE.equals(BALANCE_MIRROR_ENABLED.getValue());
        int intervalSeconds = BALANCE_MIRROR_INTERVAL_SECONDS.getValue() == null ? 15 : Math.max(5, BALANCE_MIRROR_INTERVAL_SECONDS.getValue());
        if (!enabled) {
            Registries.LOGGER.get().log("[MineStore-CoinsEngine] Balance mirror workaround disabled by config.");
            return;
        }

        balanceMirrorTask = new MineStoreScheduledTask("coinsengine-balance-mirror", this::runBalanceMirror, intervalSeconds * 1000L);
        Registries.MINESTORE_SCHEDULER.get().addTask(balanceMirrorTask);
        Registries.LOGGER.get().log("[MineStore-CoinsEngine] Balance mirror workaround enabled (interval: " + intervalSeconds + "s).");
    }

    private void runBalanceMirror() {
        MysqlConfig cfg = resolveMineStoreMysqlConfig();
        if (cfg == null || !cfg.enabled) {
            return;
        }

        String[] jdbcUrls = new String[] {
            "jdbc:mariadb://" + cfg.host + ":" + cfg.port + "/" + cfg.database,
            "jdbc:mysql://" + cfg.host + ":" + cfg.port + "/" + cfg.database
        };

        final String sql = "INSERT INTO playerdata (uuid, username, prefix, suffix, balance, player_group) VALUES (?, ?, '', '', ?, '') "
            + "ON DUPLICATE KEY UPDATE username = VALUES(username), balance = VALUES(balance)";

        Exception lastError = null;
        for (String jdbcUrl : jdbcUrls) {
            try (Connection conn = DriverManager.getConnection(jdbcUrl, cfg.username, cfg.password);
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                Set<AbstractUser> players = Registries.USER_GETTER.get().getAllPlayers();
                int rows = 0;
                for (AbstractUser abstractUser : players) {
                    if (abstractUser == null || abstractUser.commonUser() == null) {
                        continue;
                    }
                    CommonUser user = abstractUser.commonUser();
                    if (user.getUUID() == null || user.getName() == null) {
                        continue;
                    }
                    ps.setString(1, user.getUUID().toString());
                    ps.setString(2, user.getName());
                    ps.setDouble(3, user.getBalance());
                    ps.addBatch();
                    rows++;
                }
                if (rows > 0) {
                    ps.executeBatch();
                }
                return;
            } catch (Exception e) {
                lastError = e;
            }
        }

        if (lastError != null) {
            Registries.LOGGER.get().log("[MineStore-CoinsEngine] Balance mirror failed: " + lastError.getClass().getSimpleName() + " - " + lastError.getMessage());
        }
    }

    private MysqlConfig resolveMineStoreMysqlConfig() {
        try {
            Class<?> mysqlKeys = Class.forName("me.chrommob.minestore.common.config.ConfigKeys$MYSQL_KEYS");
            Object enabledKey = mysqlKeys.getField("ENABLED").get(null);
            Object hostKey = mysqlKeys.getField("IP").get(null);
            Object portKey = mysqlKeys.getField("PORT").get(null);
            Object dbKey = mysqlKeys.getField("DATABASE").get(null);
            Object userKey = mysqlKeys.getField("USERNAME").get(null);
            Object passKey = mysqlKeys.getField("PASSWORD").get(null);

            java.lang.reflect.Method getValue = enabledKey.getClass().getMethod("getValue");
            boolean enabled = Boolean.TRUE.equals(getValue.invoke(enabledKey));
            String host = String.valueOf(getValue.invoke(hostKey));
            Number portNumber = (Number) getValue.invoke(portKey);
            String database = String.valueOf(getValue.invoke(dbKey));
            String username = String.valueOf(getValue.invoke(userKey));
            String password = String.valueOf(getValue.invoke(passKey));
            return new MysqlConfig(enabled, host, portNumber != null ? portNumber.intValue() : 3306, database, username, password);
        } catch (Exception e) {
            if (!loggedMysqlConfigError) {
                loggedMysqlConfigError = true;
                Registries.LOGGER.get().log("[MineStore-CoinsEngine] Unable to read MineStore MySQL config: " + e.getClass().getSimpleName());
            }
            return null;
        }
    }

    private static final class MysqlConfig {
        private final boolean enabled;
        private final String host;
        private final int port;
        private final String database;
        private final String username;
        private final String password;

        private MysqlConfig(boolean enabled, String host, int port, String database, String username, String password) {
            this.enabled = enabled;
            this.host = host;
            this.port = port;
            this.database = database;
            this.username = username;
            this.password = password;
        }
    }
}
