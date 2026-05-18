package com.controlbro.besteconomy.coinflip;

import java.math.BigDecimal;
import java.util.UUID;

class CoinflipGame {
    final UUID creator;
    final BigDecimal bet;
    UUID challenger;
    Side creatorSide;
    State state = State.OPEN;

    CoinflipGame(UUID creator, BigDecimal bet) {
        this.creator = creator;
        this.bet = bet;
    }

    enum State {
        OPEN,
        AWAITING_CREATOR_PICK,
        FLIPPING
    }

    enum Side {
        HEADS,
        TAILS;

        Side opposite() {
            return this == HEADS ? TAILS : HEADS;
        }

        static Side parse(String input) {
            if (input == null) {
                return null;
            }
            if (input.equalsIgnoreCase("heads") || input.equalsIgnoreCase("head") || input.equalsIgnoreCase("h")) {
                return HEADS;
            }
            if (input.equalsIgnoreCase("tails") || input.equalsIgnoreCase("tail") || input.equalsIgnoreCase("t")) {
                return TAILS;
            }
            return null;
        }

        String display() {
            return this == HEADS ? "Heads" : "Tails";
        }
    }
}
