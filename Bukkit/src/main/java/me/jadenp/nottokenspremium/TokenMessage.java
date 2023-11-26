package me.jadenp.nottokenspremium;

public class TokenMessage {
    private double tokenChange = 0;
    private final long lastMessageTime;

    /**
     * Record of the token change since last token message
     */
    public TokenMessage() {
        lastMessageTime = System.currentTimeMillis();
    }

    /**
     * Add token change to the recorded value
     * @param tokenChange The amount of tokens that have been changed
     */
    public void addTokenChange(double tokenChange) {
        this.tokenChange += tokenChange;
    }

    /**
     * Get the tokens that have changed since last message
     * @return Amount of tokens that have changed. A negative change means the player has lost tokens
     */
    public double getTokenChange() {
        return tokenChange;
    }

    /**
     * Get the time when the last token change message was sent in milliseconds
     * @return The last token change message
     */
    public long getLastMessageTime() {
        return lastMessageTime;
    }
}
