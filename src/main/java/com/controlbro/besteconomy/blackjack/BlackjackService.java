package com.controlbro.besteconomy.blackjack;

import com.controlbro.besteconomy.currency.Currency;
import com.controlbro.besteconomy.currency.CurrencyManager;
import com.controlbro.besteconomy.data.EconomyManager;
import com.controlbro.besteconomy.message.MessageManager;
import com.controlbro.besteconomy.util.ColorUtil;
import com.controlbro.besteconomy.util.NumberUtil;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

public class BlackjackService implements Listener {
    private static final int INVENTORY_SIZE = 54;
    private static final int HIT_SLOT = 47;
    private static final int STAND_SLOT = 51;
    private static final int[] DEALER_SLOTS = {10, 11, 12, 13, 14, 15, 16, 17, 18};
    private static final int[] PLAYER_SLOTS = {28, 29, 30, 31, 32, 33, 34, 35, 36};

    private final JavaPlugin plugin;
    private final EconomyManager economyManager;
    private final CurrencyManager currencyManager;
    private final MessageManager messageManager;
    private final Map<UUID, BlackjackGame> games = new java.util.HashMap<>();

    public BlackjackService(JavaPlugin plugin, EconomyManager economyManager, CurrencyManager currencyManager, MessageManager messageManager) {
        this.plugin = plugin;
        this.economyManager = economyManager;
        this.currencyManager = currencyManager;
        this.messageManager = messageManager;
    }

    public boolean hasActiveGame(Player player) {
        return games.containsKey(player.getUniqueId());
    }

    public void refundActiveGames() {
        Currency shards = currencyManager.getCurrency("shards");
        if (shards == null) {
            games.clear();
            return;
        }
        for (BlackjackGame game : games.values()) {
            economyManager.addBalance(game.player, shards, game.bet);
        }
        games.clear();
    }

    public void startGame(Player player, BigDecimal bet) {
        Currency shards = currencyManager.getCurrency("shards");
        if (shards == null) {
            messageManager.send(player, "blackjack.shards-missing", null);
            return;
        }
        if (economyManager.getAvailableToSpend(player.getUniqueId(), shards).compareTo(bet) < 0) {
            messageManager.send(player, "insufficient-shards", null);
            return;
        }
        economyManager.subtractBalance(player.getUniqueId(), shards, bet);
        BlackjackGame game = new BlackjackGame(player.getUniqueId(), bet, freshDeck());
        deal(game.playerHand, game);
        deal(game.dealerHand, game);
        deal(game.playerHand, game);
        deal(game.dealerHand, game);
        games.put(player.getUniqueId(), game);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0F, 1.1F);
        messageManager.send(player, "blackjack.started", Map.of("bet", NumberUtil.format(bet)));
        if (handValue(game.playerHand) == 21) {
            stand(player, game);
            return;
        }
        openGame(player);
    }

    public void openGame(Player player) {
        BlackjackGame game = games.get(player.getUniqueId());
        if (game == null) {
            messageManager.send(player, "blackjack.usage", null);
            return;
        }
        player.openInventory(inventory(game));
        if (game.phase == Phase.DEALER_TURN) {
            scheduleDealerStep(player.getUniqueId(), game);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getInventory().getHolder() instanceof BlackjackHolder)) {
            return;
        }
        event.setCancelled(true);
        BlackjackGame game = games.get(player.getUniqueId());
        if (game == null) {
            player.closeInventory();
            return;
        }
        if (game.phase != Phase.PLAYER_TURN) {
            return;
        }
        int rawSlot = event.getRawSlot();
        if (rawSlot == HIT_SLOT) {
            hit(player, game);
        } else if (rawSlot == STAND_SLOT) {
            stand(player, game);
        }
    }

    private void hit(Player player, BlackjackGame game) {
        deal(game.playerHand, game);
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.8F, 1.4F);
        int value = handValue(game.playerHand);
        if (value > 21) {
            finish(player, game, Outcome.LOSS, "blackjack.busted");
            return;
        }
        if (value == 21) {
            stand(player, game);
            return;
        }
        openGame(player);
    }

    private void stand(Player player, BlackjackGame game) {
        game.phase = Phase.DEALER_TURN;
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8F, 0.9F);
        openGame(player);
        scheduleDealerStep(player.getUniqueId(), game);
    }

    private void scheduleDealerStep(UUID playerId, BlackjackGame game) {
        if (game.dealerTaskQueued || game.phase != Phase.DEALER_TURN) {
            return;
        }
        game.dealerTaskQueued = true;
        Bukkit.getScheduler().runTaskLater(plugin, () -> dealerStep(playerId), 12L);
    }

    private void dealerStep(UUID playerId) {
        BlackjackGame game = games.get(playerId);
        Player player = Bukkit.getPlayer(playerId);
        if (game != null) {
            game.dealerTaskQueued = false;
        }
        if (game == null || player == null) {
            return;
        }
        if (handValue(game.dealerHand) < 17) {
            deal(game.dealerHand, game);
            player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.8F, 1.0F);
            openGame(player);
            return;
        }
        resolve(player, game);
    }

    private void resolve(Player player, BlackjackGame game) {
        int playerValue = handValue(game.playerHand);
        int dealerValue = handValue(game.dealerHand);
        if (dealerValue > 21 || playerValue > dealerValue) {
            finish(player, game, isNaturalBlackjack(game.playerHand) ? Outcome.BLACKJACK : Outcome.WIN, "blackjack.win");
        } else if (playerValue == dealerValue) {
            finish(player, game, Outcome.PUSH, "blackjack.push");
        } else {
            finish(player, game, Outcome.LOSS, "blackjack.loss");
        }
    }

    private void finish(Player player, BlackjackGame game, Outcome outcome, String messagePath) {
        games.remove(player.getUniqueId());
        game.phase = Phase.FINISHED;
        Currency shards = currencyManager.getCurrency("shards");
        BigDecimal payout = payout(game.bet, outcome);
        if (shards != null && payout.compareTo(BigDecimal.ZERO) > 0) {
            economyManager.addBalance(player.getUniqueId(), shards, payout);
        }
        player.openInventory(inventory(game));
        if (outcome == Outcome.LOSS) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0F, 0.8F);
        } else if (outcome == Outcome.PUSH) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8F, 1.0F);
        } else {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0F, outcome == Outcome.BLACKJACK ? 1.6F : 1.2F);
        }
        messageManager.send(player, messagePath, Map.of(
            "bet", NumberUtil.format(game.bet),
            "payout", NumberUtil.format(payout),
            "player", Integer.toString(handValue(game.playerHand)),
            "dealer", Integer.toString(handValue(game.dealerHand))));
        if (outcome == Outcome.WIN || outcome == Outcome.BLACKJACK) {
            broadcast("blackjack.announce-win", Map.of(
                "player", player.getName(),
                "amount", NumberUtil.format(payout),
                "hand", Integer.toString(handValue(game.playerHand))));
        }
    }

    private Inventory inventory(BlackjackGame game) {
        Inventory inventory = Bukkit.createInventory(new BlackjackHolder(), INVENTORY_SIZE, color("&8Blackjack &7- &5✦" + NumberUtil.format(game.bet)));
        ItemStack filler = item(Material.BLACK_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, filler);
        }
        boolean revealDealer = game.phase != Phase.PLAYER_TURN;
        inventory.setItem(4, infoItem(game, revealDealer));
        placeHand(inventory, game.dealerHand, DEALER_SLOTS, revealDealer, "&cDealer");
        placeHand(inventory, game.playerHand, PLAYER_SLOTS, true, "&aPlayer");
        if (game.phase == Phase.PLAYER_TURN) {
            inventory.setItem(HIT_SLOT, item(Material.LIME_CONCRETE, "&a&lHIT", List.of("&7Draw another card.", "&aClick to hit.")));
            inventory.setItem(STAND_SLOT, item(Material.RED_CONCRETE, "&c&lSTAND", List.of("&7End your turn and let", "&7the dealer play.", "&cClick to stand.")));
        } else if (game.phase == Phase.DEALER_TURN) {
            inventory.setItem(49, item(Material.YELLOW_CONCRETE, "&eDealer Turn", List.of("&7Dealer hits until 17+.", "&7Please wait...")));
        } else {
            inventory.setItem(49, item(Material.NETHER_STAR, "&6Game Over", List.of("&7Start another game with", "&e/bj <amount>&7.")));
        }
        return inventory;
    }

    private void placeHand(Inventory inventory, List<Card> hand, int[] slots, boolean revealAll, String owner) {
        for (int i = 0; i < hand.size() && i < slots.length; i++) {
            if (!revealAll && i == 1) {
                inventory.setItem(slots[i], item(Material.PAPER, "&8Hidden Card", List.of("&7Dealer's face-down card.")));
            } else {
                inventory.setItem(slots[i], cardItem(hand.get(i), owner));
            }
        }
    }

    private ItemStack infoItem(BlackjackGame game, boolean revealDealer) {
        String dealerValue = revealDealer ? Integer.toString(handValue(game.dealerHand)) : Integer.toString(visibleDealerValue(game.dealerHand));
        return item(Material.BOOK, "&6Blackjack", List.of(
            "&7Bet: &5✦" + NumberUtil.format(game.bet),
            "&7Your Hand: &e" + handValue(game.playerHand),
            "&7Dealer: &e" + dealerValue + (revealDealer ? "" : "+?"),
            "&7Win pays &a" + multiplier("blackjack.win-payout-multiplier", "2") + "x&7, blackjack pays &a" + multiplier("blackjack.blackjack-payout-multiplier", "2.5") + "x&7.",
            "&7Dealer stands on 17."));
    }

    private ItemStack cardItem(Card card, String owner) {
        return item(Material.PAPER, card.displayName(), List.of(owner, "&7Value: &e" + card.blackjackValue(), "&8Paper renamed as a card"));
    }

    private List<Card> freshDeck() {
        List<Card> deck = new ArrayList<>();
        for (Suit suit : Suit.values()) {
            for (Rank rank : Rank.values()) {
                deck.add(new Card(rank, suit));
            }
        }
        Collections.shuffle(deck);
        return deck;
    }

    private void deal(List<Card> hand, BlackjackGame game) {
        if (game.deck.isEmpty()) {
            game.deck.addAll(freshDeck());
        }
        hand.add(game.deck.remove(game.deck.size() - 1));
    }

    private int visibleDealerValue(List<Card> hand) {
        if (hand.isEmpty()) {
            return 0;
        }
        return handValue(List.of(hand.get(0)));
    }

    private int handValue(List<Card> hand) {
        int total = 0;
        int aces = 0;
        for (Card card : hand) {
            total += card.blackjackValue();
            if (card.rank == Rank.ACE) {
                aces++;
            }
        }
        while (total > 21 && aces > 0) {
            total -= 10;
            aces--;
        }
        return total;
    }

    private boolean isNaturalBlackjack(List<Card> hand) {
        return hand.size() == 2 && handValue(hand) == 21;
    }

    private BigDecimal payout(BigDecimal bet, Outcome outcome) {
        return switch (outcome) {
            case BLACKJACK -> bet.multiply(multiplier("blackjack.blackjack-payout-multiplier", "2.5")).setScale(2, RoundingMode.DOWN).stripTrailingZeros();
            case WIN -> bet.multiply(multiplier("blackjack.win-payout-multiplier", "2")).setScale(2, RoundingMode.DOWN).stripTrailingZeros();
            case PUSH -> bet;
            case LOSS -> BigDecimal.ZERO;
        };
    }

    private BigDecimal multiplier(String path, String fallback) {
        try {
            return new BigDecimal(plugin.getConfig().getString(path, fallback));
        } catch (NumberFormatException ex) {
            return new BigDecimal(fallback);
        }
    }

    private void broadcast(String path, Map<String, String> placeholders) {
        Component message = messageManager.getMessage(path, null, placeholders);
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(message);
        }
    }

    private ItemStack item(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(color(name));
        List<Component> lines = new ArrayList<>();
        for (String line : lore) {
            lines.add(color(line));
        }
        meta.lore(lines);
        item.setItemMeta(meta);
        return item;
    }

    private Component color(String text) {
        return ColorUtil.colorize(text == null ? "" : text);
    }

    private record BlackjackHolder() implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    private enum Phase { PLAYER_TURN, DEALER_TURN, FINISHED }

    private enum Outcome { BLACKJACK, WIN, PUSH, LOSS }

    private enum Suit {
        HEARTS("♥", "&c"), DIAMONDS("♦", "&b"), CLUBS("♣", "&8"), SPADES("♠", "&f");

        private final String symbol;
        private final String color;

        Suit(String symbol, String color) {
            this.symbol = symbol;
            this.color = color;
        }
    }

    private enum Rank {
        ACE("A", 11), TWO("2", 2), THREE("3", 3), FOUR("4", 4), FIVE("5", 5), SIX("6", 6), SEVEN("7", 7),
        EIGHT("8", 8), NINE("9", 9), TEN("10", 10), JACK("J", 10), QUEEN("Q", 10), KING("K", 10);

        private final String label;
        private final int value;

        Rank(String label, int value) {
            this.label = label;
            this.value = value;
        }
    }

    private record Card(Rank rank, Suit suit) {
        private String displayName() {
            return suit.color + rank.label + suit.symbol + " &7(" + rank.value + ")";
        }

        private int blackjackValue() {
            return rank.value;
        }
    }

    private static final class BlackjackGame {
        private final UUID player;
        private final BigDecimal bet;
        private final List<Card> deck;
        private final List<Card> playerHand = new ArrayList<>();
        private final List<Card> dealerHand = new ArrayList<>();
        private Phase phase = Phase.PLAYER_TURN;
        private boolean dealerTaskQueued;

        private BlackjackGame(UUID player, BigDecimal bet, List<Card> deck) {
            this.player = player;
            this.bet = bet;
            this.deck = deck;
        }
    }
}
