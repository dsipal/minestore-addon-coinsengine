package me.chrommob.minestore.coinsengine.provider;

import me.chrommob.minestore.api.Registries;
import me.chrommob.minestore.api.interfaces.economyInfo.PlayerEconomyProvider;
import me.chrommob.minestore.api.interfaces.user.CommonUser;
import su.nightexpress.coinsengine.api.CoinsEngineAPI;
import su.nightexpress.coinsengine.api.currency.Currency;

import java.util.UUID;

/**
 * Economy provider that delegates balance and take operations to CoinsEngine
 * for a single configured currency.
 */
public class CoinsEngineEconomyProvider implements PlayerEconomyProvider {

    private final String currencyId;

    public CoinsEngineEconomyProvider(String currencyId) {
        this.currencyId = (currencyId == null || currencyId.trim().isEmpty()) ? "points" : currencyId;
    }

    @Override
    public double getBalance(CommonUser commonUser) {
        if (commonUser == null || !CoinsEngineAPI.isLoaded()) {
            return 0.0;
        }
        Currency currency = CoinsEngineAPI.getCurrency(currencyId);
        if (currency == null) {
            return 0.0;
        }
        UUID uuid = commonUser.getUUID();
        if (uuid == null) {
            return 0.0;
        }
        return CoinsEngineAPI.getBalance(uuid, currency);
    }

    @Override
    public boolean takeMoney(CommonUser commonUser, double amount) {
        String username = commonUser != null ? commonUser.getName() : null;
        if (username == null || username.trim().isEmpty()) {
            try {
                Registries.LOGGER.get().log("[MineStore-CoinsEngine] Rejected virtual charge due to blank username context.");
            } catch (Exception ignored) {
            }
            return false;
        }
        if (amount <= 0 || !CoinsEngineAPI.isLoaded()) {
            return false;
        }
        Currency currency = CoinsEngineAPI.getCurrency(currencyId);
        if (currency == null) {
            return false;
        }
        UUID uuid = commonUser.getUUID();
        if (uuid == null) {
            return false;
        }
        double balance = CoinsEngineAPI.getBalance(uuid, currency);
        if (balance < amount) {
            return false;
        }
        return CoinsEngineAPI.removeBalance(uuid, currency, amount);
    }
}
