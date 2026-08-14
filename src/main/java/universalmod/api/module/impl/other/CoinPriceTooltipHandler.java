package universalmod.api.module.impl.other;

import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public final class CoinPriceTooltipHandler {
    private CoinPriceTooltipHandler() {
    }

    public static void appendCoinPrice(List<Component> lines) {
        if (!CoinPrice.isActive()) {
            return;
        }

        double averageRate = CoinRateService.getAverageRate();
        if (averageRate <= 0.0D || Double.isNaN(averageRate) || Double.isInfinite(averageRate)) {
            return;
        }

        for (int index = 0; index < lines.size(); index++) {
            Component original = lines.get(index);
            String rawText = original.getString();
            if (PriceLineParser.hasCoinSuffix(rawText) || !PriceLineParser.isSupportedPriceLine(rawText)) {
                continue;
            }

            Double price = PriceLineParser.extractPrice(rawText);
            if (price == null || price <= 0.0D) {
                continue;
            }

            String formattedCoins = String.format(Locale.US, "%.2f", price / averageRate);
            MutableComponent appended = original.copy()
                    .append(Component.literal(" (").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(formattedCoins + " ").withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal("|").withStyle(ChatFormatting.GOLD))
                    .append(Component.literal("❘").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
                    .append(Component.literal("|").withStyle(ChatFormatting.GOLD))
                    .append(Component.literal(")").withStyle(ChatFormatting.GRAY));
            lines.set(index, appended);
        }
    }
}
