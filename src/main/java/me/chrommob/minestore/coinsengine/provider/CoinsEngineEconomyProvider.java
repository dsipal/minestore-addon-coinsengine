package me.chrommob.minestore.coinsengine.provider;

import me.chrommob.minestore.api.Registries;
import me.chrommob.minestore.api.interfaces.economyInfo.PlayerEconomyProvider;
import me.chrommob.minestore.api.interfaces.user.CommonUser;
import su.nightexpress.coinsengine.api.CoinsEngineAPI;
import su.nightexpress.coinsengine.api.currency.Currency;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

/**
 * Economy provider that delegates balance and take operations to CoinsEngine
 * for a single configured currency.
 */
public class CoinsEngineEconomyProvider implements PlayerEconomyProvider {

    private final String currencyId;
    private static final String DEBUG_ENDPOINT = "http://127.0.0.1:7351/ingest/be281d73-124c-45fc-9945-79f4d4c44043";
    private static final String DEBUG_SESSION = "93aa5c";
    private static final String DEBUG_RUN = "server-run";

    public CoinsEngineEconomyProvider(String currencyId) {
        this.currencyId = (currencyId == null || currencyId.trim().isEmpty()) ? "points" : currencyId;
    }

    @Override
    public double getBalance(CommonUser commonUser) {
        String username = commonUser != null ? commonUser.getName() : null;
        // #region agent log
        debugLog("H10", "CoinsEngineEconomyProvider.java:getBalance:entry", "getBalance called", "\"hasUser\":" + (commonUser != null) + ",\"username\":\"" + escapeJson(username) + "\"");
        // #endregion
        if (!CoinsEngineAPI.isLoaded()) {
            // #region agent log
            debugLog("H3", "CoinsEngineEconomyProvider.java:getBalance:coinsengine-not-loaded", "CoinsEngine not loaded in getBalance", "\"currencyId\":\"" + escapeJson(currencyId) + "\"");
            // #endregion
            try {
                Registries.LOGGER.get().log("[MineStore-CoinsEngine][debug] getBalance skipped: CoinsEngine not loaded");
            } catch (Exception ignored) {
            }
            return 0.0;
        }
        Currency currency = CoinsEngineAPI.getCurrency(currencyId);
        if (currency == null) {
            // #region agent log
            debugLog("H2", "CoinsEngineEconomyProvider.java:getBalance:currency-null", "Configured currency not found", "\"currencyId\":\"" + escapeJson(currencyId) + "\"");
            // #endregion
            try {
                Registries.LOGGER.get().log("[MineStore-CoinsEngine][debug] getBalance skipped: unknown currency_id=" + currencyId);
            } catch (Exception ignored) {
            }
            return 0.0;
        }
        UUID uuid = commonUser.getUUID();
        if (uuid == null) {
            // #region agent log
            debugLog("H10", "CoinsEngineEconomyProvider.java:getBalance:uuid-null", "User UUID is null in getBalance", "\"currencyId\":\"" + escapeJson(currencyId) + "\",\"username\":\"" + escapeJson(username) + "\"");
            // #endregion
            try {
                Registries.LOGGER.get().log("[MineStore-CoinsEngine][debug] getBalance skipped: user UUID is null");
            } catch (Exception ignored) {
            }
            return 0.0;
        }
        double balance = CoinsEngineAPI.getBalance(uuid, currency);
        // #region agent log
        debugLog("H10", "CoinsEngineEconomyProvider.java:getBalance:result", "Resolved player balance", "\"username\":\"" + escapeJson(username) + "\",\"uuid\":\"" + uuid + "\",\"currencyId\":\"" + escapeJson(currencyId) + "\",\"balance\":" + balance);
        // #endregion
        try {
            Registries.LOGGER.get().log("[MineStore-CoinsEngine][debug] getBalance user=" + username + " uuid=" + uuid + " currency=" + currencyId + " balance=" + balance);
        } catch (Exception ignored) {
        }
        return balance;
    }

    @Override
    public boolean takeMoney(CommonUser commonUser, double amount) {
        String username = commonUser != null ? commonUser.getName() : null;
        // #region agent log
        debugLog("H10", "CoinsEngineEconomyProvider.java:takeMoney:entry", "takeMoney called", "\"amount\":" + amount + ",\"hasUser\":" + (commonUser != null) + ",\"username\":\"" + escapeJson(username) + "\"");
        // #endregion
        if (username == null || username.trim().isEmpty()) {
            // #region agent log
            debugLog("H11", "CoinsEngineEconomyProvider.java:takeMoney:blank-username", "Rejected charge with blank username context", "\"amount\":" + amount);
            // #endregion
            try {
                Registries.LOGGER.get().log("[MineStore-CoinsEngine][debug] takeMoney rejected: blank username context");
            } catch (Exception ignored) {
            }
            return false;
        }
        if (amount <= 0 || !CoinsEngineAPI.isLoaded()) {
            // #region agent log
            debugLog("H3", "CoinsEngineEconomyProvider.java:takeMoney:invalid-amount-or-api", "Early return in takeMoney", "\"amount\":" + amount + ",\"coinsEngineLoaded\":" + CoinsEngineAPI.isLoaded());
            // #endregion
            try {
                Registries.LOGGER.get().log("[MineStore-CoinsEngine][debug] takeMoney rejected: amount=" + amount + " coinsEngineLoaded=" + CoinsEngineAPI.isLoaded());
            } catch (Exception ignored) {
            }
            return false;
        }
        Currency currency = CoinsEngineAPI.getCurrency(currencyId);
        if (currency == null) {
            // #region agent log
            debugLog("H2", "CoinsEngineEconomyProvider.java:takeMoney:currency-null", "Configured currency not found for takeMoney", "\"currencyId\":\"" + escapeJson(currencyId) + "\"");
            // #endregion
            try {
                Registries.LOGGER.get().log("[MineStore-CoinsEngine][debug] takeMoney rejected: unknown currency_id=" + currencyId);
            } catch (Exception ignored) {
            }
            return false;
        }
        UUID uuid = commonUser.getUUID();
        if (uuid == null) {
            // #region agent log
            debugLog("H10", "CoinsEngineEconomyProvider.java:takeMoney:uuid-null", "User UUID is null in takeMoney", "\"currencyId\":\"" + escapeJson(currencyId) + "\",\"username\":\"" + escapeJson(username) + "\"");
            // #endregion
            try {
                Registries.LOGGER.get().log("[MineStore-CoinsEngine][debug] takeMoney rejected: user UUID is null");
            } catch (Exception ignored) {
            }
            return false;
        }
        double balance = CoinsEngineAPI.getBalance(uuid, currency);
        if (balance < amount) {
            // #region agent log
        debugLog("H10", "CoinsEngineEconomyProvider.java:takeMoney:insufficient", "Insufficient balance for purchase", "\"username\":\"" + escapeJson(username) + "\",\"uuid\":\"" + uuid + "\",\"currencyId\":\"" + escapeJson(currencyId) + "\",\"balance\":" + balance + ",\"amount\":" + amount);
            // #endregion
            try {
                Registries.LOGGER.get().log("[MineStore-CoinsEngine][debug] takeMoney rejected: insufficient uuid=" + uuid + " balance=" + balance + " amount=" + amount);
            } catch (Exception ignored) {
            }
            return false;
        }
        boolean removed = CoinsEngineAPI.removeBalance(uuid, currency, amount);
        // #region agent log
        debugLog("H10", "CoinsEngineEconomyProvider.java:takeMoney:result", "removeBalance result", "\"username\":\"" + escapeJson(username) + "\",\"uuid\":\"" + uuid + "\",\"currencyId\":\"" + escapeJson(currencyId) + "\",\"amount\":" + amount + ",\"removed\":" + removed);
        // #endregion
        try {
            Registries.LOGGER.get().log("[MineStore-CoinsEngine][debug] takeMoney user=" + username + " uuid=" + uuid + " currency=" + currencyId + " amount=" + amount + " removed=" + removed);
        } catch (Exception ignored) {
        }
        return removed;
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
