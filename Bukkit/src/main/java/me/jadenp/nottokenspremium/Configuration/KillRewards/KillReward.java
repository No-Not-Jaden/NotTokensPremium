package me.jadenp.nottokenspremium.Configuration.KillRewards;

import org.bukkit.entity.EntityType;

public class KillReward {
    private final double amount;
    private final double rate;

    public KillReward(double amount, double rate) {

        this.amount = amount;
        this.rate = rate;
    }

    public double getAmount() {
        return amount;
    }

    /**
     * Uses a random number (0,1) and compares if it is less than the rate
     * @return True if the random number is less than the rate
     */
    public boolean giveReward(){
        return Math.random() <= rate;
    }

}
