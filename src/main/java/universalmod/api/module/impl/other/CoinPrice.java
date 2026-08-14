package universalmod.api.module.impl.other;

import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import universalmod.api.module.Module;
import universalmod.api.module.ModuleCategory;

public final class CoinPrice extends Module {
    private static CoinPrice instance;
    private static boolean callbackRegistered;

    public CoinPrice() {
        super("CoinPrice", "Shows coin values for supported auction prices.", ModuleCategory.MISC);
        instance = this;
        if (!callbackRegistered) {
            callbackRegistered = true;
            ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> CoinPriceTooltipHandler.appendCoinPrice(lines));
        }
    }

    public static boolean isActive() {
        return instance != null && instance.isEnabled();
    }

    @Override
    protected void onEnable() {
        CoinRateService.start();
        CoinRateService.refreshNow();
    }
}
