package com.enjin.enjincoin.spigot_framework.listeners.notifications;

import com.enjin.enjincoin.sdk.service.notifications.vo.NotificationEvent;
import com.enjin.enjincoin.spigot_framework.player.MinecraftPlayer;
import com.enjin.enjincoin.spigot_framework.player.PlayerManager;
import com.enjin.enjincoin.spigot_framework.trade.TradeManager;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.enjin.enjincoin.sdk.enums.NotificationType;
import com.enjin.enjincoin.sdk.service.identities.vo.Identity;
import com.enjin.enjincoin.sdk.service.identities.vo.IdentityField;
import com.enjin.enjincoin.sdk.service.notifications.NotificationListener;
import com.enjin.enjincoin.sdk.service.tokens.vo.Token;
import com.enjin.enjincoin.spigot_framework.BasePlugin;
import com.enjin.enjincoin.spigot_framework.inventory.WalletInventory;
import com.enjin.enjincoin.spigot_framework.util.UuidUtils;
import com.enjin.enjincoin.spigot_framework.util.MessageUtils;
import net.kyori.text.TextComponent;
import net.kyori.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.IOException;
import java.util.*;

/**
 * <p>A listener for handling Enjin Coin SDK events.</p>
 */
// TODO: Refactor around MinecraftPlayer
public class GenericNotificationListener implements NotificationListener {

    /**
     * <p>The spigot plugin.</p>
     */
    private BasePlugin main;

    /**
     * <p>Listener constructor.</p>
     *
     * @param main the Spigot plugin
     */
    public GenericNotificationListener(BasePlugin main) {
        this.main = main;
    }

    @Override
    public void notificationReceived(NotificationEvent event) {
        NotificationType eventType = event.getNotificationType();

        this.main.getBootstrap().debug(String.format("Received %s event on %s with data: %s", eventType, event.getChannel(), event.getSourceData()));
        this.main.getBootstrap().debug(String.format("Parsing data for %s event", event.getNotificationType().getEventType()));
        JsonParser parser = new JsonParser();
        JsonObject data = parser.parse(event.getSourceData()).getAsJsonObject().get("data").getAsJsonObject();

        // txr_ => transaction request
        // tx_ => transaction
        if (eventType == NotificationType.TXR_PENDING) {
            this.main.getBootstrap().debug("Transaction is pending");
        } else if (eventType == NotificationType.TX_EXECUTED) {
            this.main.getBootstrap().debug("Transaction is executed");
            JsonElement eventElement = data.get("event");
            if (eventElement != null) {
                String eventString = eventElement.getAsString();

                if (eventString.equalsIgnoreCase("Transfer")) {
                    // handle transfer event.
                    String fromEthereumAddress = data.get("param1").getAsString();
                    String toEthereumAddress = data.get("param2").getAsString();
                    String tokenId = data.get("token").getAsJsonObject().get("token_id").getAsString();
                    String amount = data.get("param3").getAsString();

                    PlayerManager playerManager = this.main.getBootstrap().getPlayerManager();
                    MinecraftPlayer fromPlayer = playerManager.getPlayer(fromEthereumAddress);
                    MinecraftPlayer toPlayer = playerManager.getPlayer(toEthereumAddress);

                    if (fromPlayer != null) {
                        Bukkit.getScheduler().runTaskAsynchronously(main, () -> fromPlayer.reloadUser());
                        TextComponent text = TextComponent.of("You have successfully sent ").color(TextColor.GOLD)
                                .append(TextComponent.of(amount).color(TextColor.GREEN))
                                .append(TextComponent.of(" " + data.get("token").getAsJsonObject().get("name").getAsString()).color(TextColor.DARK_PURPLE));
                        MessageUtils.sendMessage(fromPlayer.getBukkitPlayer(), text);
                    }

                    if (toPlayer != null) {
                        Bukkit.getScheduler().runTaskAsynchronously(main, () -> toPlayer.reloadUser());
                        TextComponent text = TextComponent.of("You have received ").color(TextColor.GOLD)
                                .append(TextComponent.of(amount).color(TextColor.GREEN))
                                .append(TextComponent.of(" " + data.get("token").getAsJsonObject().get("name").getAsString()).color(TextColor.DARK_PURPLE));
                        MessageUtils.sendMessage(toPlayer.getBukkitPlayer(), text);
                    }
                } else if (eventString.equalsIgnoreCase("CreateTrade")) {
                    int requestId = data.get("transaction_id").getAsInt();
                    String tradeId = data.get("param1").getAsString();
                    TradeManager manager = main.getBootstrap().getTradeManager();
                    manager.submitCompleteTrade(requestId, tradeId);
                } else if (eventString.equalsIgnoreCase("CompleteTrade")) {
                    int requestId = data.get("transaction_id").getAsInt();
                    TradeManager manager = main.getBootstrap().getTradeManager();
                    manager.completeTrade(requestId);
                }
            }
        } else if (eventType == NotificationType.TXR_CANCELED_USER) {
            this.main.getBootstrap().debug("Transaction was canceled");
        } else if (eventType == NotificationType.IDENTITY_UPDATED) {
            MinecraftPlayer player = this.main.getBootstrap().getPlayerManager().getPlayer(data.get("id").getAsInt());
            if (player != null) {
                Bukkit.getScheduler().runTaskAsynchronously(this.main, () -> player.reloadUser());
            }
        } else {
            this.main.getBootstrap().debug("Transaction was last in state: " + eventType);
        }
    }

    /**
     * <p>Returns an {@link Token} associated with an {@link Identity}
     * of an online player that matches the provided token ID.</p>
     *
     * @param identity the identity
     * @param tokenId the token ID
     *
     * @return a {@link Token} if present or null if not present
     *
     * @since 1.0
     */
    public Token getTokenEntry(Identity identity, String tokenId) {
        Token entry = null;
        for (Token e : identity.getTokens()) {
            if (e.getTokenId().equals(tokenId)) {
                entry = e;
                break;
            }
        }
        return entry;
    }

    /**
     * <p>Add a value to a {@link Token} for the provided token ID
     * and identity.</p>
     *
     * @param identity the identity
     * @param tokenId the token ID
     * @param amount the amount
     *
     * @since 1.0
     */
    public void addTokenValue(Identity identity, String tokenId, double amount) {
//        Token entry = getTokenEntry(identity, tokenId);
//        if (entry != null)
//            entry.setBalance(entry.getBalance() + amount);
//        else {
//            List<Token> entries = new ArrayList<Token>(identity.getTokens());
//            Token token = new Token();
//            token.setTokenId(tokenId);
//            token.setBalance(amount);
//            entries.add(token);
//            identity.setTokens(entries);
//        }
//
//        updateInventory(identity, tokenId, amount);
    }

    /**
     * <p>Updates the inventory associated with an identity where a
     * menu item represents a token with the provided ID to the
     * specified amount.</p>
     *
     * @param identity the identity
     * @param tokenId the token ID
     * @param amount the amount
     */
    public void updateInventory(Identity identity, String tokenId, double amount) {
        JsonObject config = main.getBootstrap().getConfig();

        String displayName = null;
        if (config.has("tokens")) {
            JsonObject tokens = config.getAsJsonObject("tokens");
            if (tokens.has(tokenId)) {
                JsonObject token = tokens.getAsJsonObject(tokenId);
                if (token.has("displayName")) {
                    displayName = token.get("displayName").getAsString();
                } else {
                    Token spec = main.getBootstrap().getTokens().get(tokenId);
                    if (spec != null) {
                        if (spec.getName() != null) {
                            displayName = spec.getName();
                        } else {
                            displayName = "Token #" + tokenId;
                        }
                    }
                }
            }
        }

        if (displayName != null) {
            UUID uuid = null;
            for (IdentityField field : identity.getFields()) {
                if (field.getKey().equalsIgnoreCase("uuid")) {
                    uuid = UuidUtils.stringToUuid(field.getFieldValue());
                    break;
                }
            }

            Player player = null;
            if (uuid != null) {
                player = Bukkit.getPlayer(uuid);
            }

            if (player != null) {
                InventoryView view = player.getOpenInventory();
                if (view != null && ChatColor.stripColor(view.getTitle()).equalsIgnoreCase("Enjin Wallet")) {
                    ItemStack stack = null;
                    ItemMeta meta = null;
                    int i;
                    for (i = 0; i < 6 * 9; i++) {
                        stack = view.getItem(i++);
                        if (stack != null) {
                            meta = stack.getItemMeta();
                            if (ChatColor.stripColor(meta.getDisplayName()).equalsIgnoreCase(displayName))
                                break;
                        }
                        stack = null;
                        meta = null;
                    }

                    if (stack == null) {
                        if (config.has("tokens")) {
                            JsonObject tokens = config.getAsJsonObject("tokens");
                            if (tokens.has(tokenId)) {
                                JsonObject tokenDisplay = config.getAsJsonObject(tokenId);
                                Token token = main.getBootstrap().getTokens().get(tokenId);
                                if (token != null) {
                                    Material material = null;
                                    if (tokenDisplay.has("material"))
                                        material = Material.getMaterial(tokenDisplay.get("material").getAsString());
                                    if (material == null)
                                        material = Material.APPLE;

                                    stack = new ItemStack(material);
                                    meta = stack.getItemMeta();

                                    if (tokenDisplay.has("displayName")) {
                                        meta.setDisplayName(ChatColor.DARK_PURPLE + tokenDisplay.get("displayName").getAsString());
                                    } else {
                                        if (token.getName() != null)
                                            meta.setDisplayName(ChatColor.DARK_PURPLE + token.getName());
                                        else
                                            meta.setDisplayName(ChatColor.DARK_PURPLE + "Token #" + token.getTokenId());
                                    }

                                    List<String> lore = new ArrayList<>();
                                    int balance = Double.valueOf(amount).intValue();
                                    lore.add(ChatColor.GRAY + "Balance: " + ChatColor.GOLD + balance);

                                    if (tokenDisplay.has("lore")) {
                                        JsonElement element = tokenDisplay.get("lore");
                                        if (element.isJsonArray()) {
                                            JsonArray array = element.getAsJsonArray();
                                            for (JsonElement line : array) {
                                                lore.add(ChatColor.DARK_GRAY + line.getAsString());
                                            }
                                        } else {
                                            lore.add(ChatColor.DARK_GRAY + element.getAsString());
                                        }
                                    }

                                    meta.setLore(lore);
                                    stack.setItemMeta(meta);
                                    view.setItem(i - 1, stack);
                                }
                            }
                        }
                    } else {
                        List<String> lore = meta.getLore();
                        String value = ChatColor.stripColor(lore.get(0)).replace("Balance: ", "");
                        if (value.contains(".")) {
                            Double val = Double.valueOf(value) + amount;
                            lore.set(0, ChatColor.GRAY + "Balance: " + ChatColor.GOLD + WalletInventory.DECIMAL_FORMAT.format(val));
                        } else {
                            Integer val = Double.valueOf(value).intValue() + Double.valueOf(amount).intValue();
                            lore.set(0, ChatColor.GRAY + "Balance: " + ChatColor.GOLD + val);
                        }
                        meta.setLore(lore);
                        stack.setItemMeta(meta);
                        view.setItem(i - 1, stack);
                    }

                    player.updateInventory();
                }
            }
        }
    }

}