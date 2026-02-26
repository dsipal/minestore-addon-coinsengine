package me.chrommob.minestore.coinsengine;

import me.chrommob.minestore.api.Registries;
import me.chrommob.minestore.api.generic.MineStoreAddon;
import me.chrommob.minestore.api.interfaces.user.AbstractUser;
import me.chrommob.minestore.api.interfaces.user.CommonUser;
import me.chrommob.minestore.api.scheduler.MineStoreScheduledTask;
import me.chrommob.minestore.coinsengine.provider.CoinsEngineEconomyProvider;
import me.chrommob.minestore.libs.me.chrommob.config.ConfigManager.ConfigKey;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
    private static final String DEBUG_ENDPOINT = "http://127.0.0.1:7351/ingest/be281d73-124c-45fc-9945-79f4d4c44043";
    private static final String DEBUG_SESSION = "93aa5c";
    private static final String DEBUG_RUN = "server-run";

    private CoinsEngineEconomyProvider economyProvider;
    private MineStoreScheduledTask balanceMirrorTask;

    @Override
    public void onEnable() {
        // #region agent log
        debugLog("H1", "CoinsEngineAddon.java:onEnable:entry", "onEnable start", "\"addon\":\"MineStore-CoinsEngine\"");
        // #endregion
        if (!isCoinsEngineAvailable()) {
            // #region agent log
            debugLog("H2", "CoinsEngineAddon.java:onEnable:coinsengine-missing", "CoinsEngine API missing or not loaded", "\"coinsEngineAvailable\":false");
            // #endregion
            Registries.LOGGER.get().log("[MineStore-CoinsEngine] CoinsEngine is not loaded. Install CoinsEngine and restart the server.");
            return;
        }

        File addonDataFolder = getApiData() != null ? getApiData().getDataFolder() : null;
        File addonConfigFile = addonDataFolder != null ? new File(addonDataFolder, "config") : null;
        // #region agent log
        debugLog("H6", "CoinsEngineAddon.java:onEnable:config-source", "Addon config source inspection",
            "\"dataFolder\":\"" + escapeJson(addonDataFolder != null ? addonDataFolder.getAbsolutePath() : "null") + "\","
                + "\"configPath\":\"" + escapeJson(addonConfigFile != null ? addonConfigFile.getAbsolutePath() : "null") + "\","
                + "\"configExists\":" + (addonConfigFile != null && addonConfigFile.exists()));
        // #endregion
        Registries.LOGGER.get().log("[MineStore-CoinsEngine] Config source: " + (addonConfigFile != null ? addonConfigFile.getAbsolutePath() : "unavailable"));

        String currencyId = CURRENCY_ID.getValue();
        // #region agent log
        debugLog("H7", "CoinsEngineAddon.java:onEnable:currency-read", "currency_id resolved from ConfigKey",
            "\"currencyIdRaw\":\"" + escapeJson(currencyId) + "\"");
        // #endregion
        if (currencyId == null || currencyId.trim().isEmpty()) {
            currencyId = "points";
        }

        economyProvider = new CoinsEngineEconomyProvider(currencyId);
        Registries.PLAYER_ECONOMY_PROVIDER.set(economyProvider);
        // #region agent log
        debugLog("H3", "CoinsEngineAddon.java:onEnable:provider-set", "Economy provider registered", "\"currencyId\":\"" + escapeJson(currencyId) + "\"");
        // #endregion
        try {
            Object providerRef = Registries.PLAYER_ECONOMY_PROVIDER.get();
            // #region agent log
            debugLog("H3", "CoinsEngineAddon.java:onEnable:provider-verify", "Registry provider verification",
                "\"providerClass\":\"" + escapeJson(providerRef != null ? providerRef.getClass().getName() : "null") + "\"");
            // #endregion
        } catch (Exception ignored) {
        }
        startBalanceMirrorWorkaround();
        Registries.LOGGER.get().log("[MineStore-CoinsEngine] Virtual currency is now using CoinsEngine currency: " + currencyId);
    }

    private boolean isCoinsEngineAvailable() {
        try {
            Class<?> api = Class.forName("su.nightexpress.coinsengine.api.CoinsEngineAPI");
            java.lang.reflect.Method m = api.getMethod("isLoaded");
            boolean loaded = Boolean.TRUE.equals(m.invoke(null));
            // #region agent log
            debugLog("H2", "CoinsEngineAddon.java:isCoinsEngineAvailable", "CoinsEngine availability check", "\"coinsEngineLoaded\":" + loaded);
            // #endregion
            return loaded;
        } catch (Exception e) {
            // #region agent log
            debugLog("H2", "CoinsEngineAddon.java:isCoinsEngineAvailable:error", "CoinsEngine availability check failed", "\"error\":\"" + escapeJson(e.getClass().getSimpleName()) + "\"");
            // #endregion
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
        // #region agent log
        debugLog("H8", "CoinsEngineAddon.java:startBalanceMirrorWorkaround:config", "Balance mirror config evaluated",
            "\"enabled\":" + enabled + ",\"intervalSeconds\":" + intervalSeconds);
        // #endregion
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
            // #region agent log
            debugLog("H8", "CoinsEngineAddon.java:runBalanceMirror:mysql-disabled", "Skipping mirror because MineStore MySQL is disabled/unavailable", "\"mysqlEnabled\":false");
            // #endregion
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
                // #region agent log
                debugLog("H9", "CoinsEngineAddon.java:runBalanceMirror:success", "Balance mirror write succeeded",
                    "\"rows\":" + rows + ",\"jdbc\":\"" + escapeJson(jdbcUrl) + "\"");
                // #endregion
                Registries.LOGGER.get().log("[MineStore-CoinsEngine][debug] balance mirror wrote " + rows + " player rows");
                return;
            } catch (Exception e) {
                lastError = e;
            }
        }

        if (lastError != null) {
            // #region agent log
            debugLog("H9", "CoinsEngineAddon.java:runBalanceMirror:error", "Balance mirror write failed",
                "\"error\":\"" + escapeJson(lastError.getClass().getSimpleName()) + "\",\"message\":\"" + escapeJson(String.valueOf(lastError.getMessage())) + "\"");
            // #endregion
            Registries.LOGGER.get().log("[MineStore-CoinsEngine][debug] balance mirror failed: " + lastError.getClass().getSimpleName() + " - " + lastError.getMessage());
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

            // #region agent log
            debugLog("H8", "CoinsEngineAddon.java:resolveMineStoreMysqlConfig", "Resolved MineStore MySQL config via reflection",
                "\"enabled\":" + enabled + ",\"host\":\"" + escapeJson(host) + "\",\"port\":" + (portNumber != null ? portNumber.intValue() : -1) + ",\"database\":\"" + escapeJson(database) + "\"");
            // #endregion
            return new MysqlConfig(enabled, host, portNumber != null ? portNumber.intValue() : 3306, database, username, password);
        } catch (Exception e) {
            // #region agent log
            debugLog("H8", "CoinsEngineAddon.java:resolveMineStoreMysqlConfig:error", "Failed to resolve MineStore MySQL config",
                "\"error\":\"" + escapeJson(e.getClass().getSimpleName()) + "\"");
            // #endregion
            Registries.LOGGER.get().log("[MineStore-CoinsEngine][debug] unable to read MineStore MySQL config: " + e.getClass().getSimpleName());
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

    private static String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static void debugLog(String hypothesisId, String location, String message, String dataJsonContent) {
        try {
            String payload = "{"
                + "\"sessionId\":\"" + DEBUG_SESSION + "\","
                + "\"runId\":\"" + DEBUG_RUN + "\","
                + "\"hypothesisId\":\"" + hypothesisId + "\","
                + "\"location\":\"" + location + "\","
                + "\"message\":\"" + escapeJson(message) + "\","
                + "\"data\":{" + dataJsonContent + "},"
                + "\"timestamp\":" + System.currentTimeMillis()
                + "}";

            HttpRequest request = HttpRequest.newBuilder(URI.create(DEBUG_ENDPOINT))
                .header("Content-Type", "application/json")
                .header("X-Debug-Session-Id", DEBUG_SESSION)
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

            HttpClient.newHttpClient().sendAsync(request, HttpResponse.BodyHandlers.discarding());
        } catch (Exception ignored) {
        }
    }
}
