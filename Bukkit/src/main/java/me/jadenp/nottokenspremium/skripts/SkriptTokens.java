package me.jadenp.nottokenspremium.skripts;

import ch.njol.skript.Skript;
import ch.njol.skript.classes.Changer;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.ExpressionType;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import ch.njol.util.coll.CollectionUtils;
import me.jadenp.nottokenspremium.TokenManager;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.Event;
import org.jetbrains.annotations.NotNull;

public class SkriptTokens extends SimpleExpression<Double> {

    static {
        Skript.registerExpression(SkriptTokens.class, Double.class, ExpressionType.COMBINED, "[the] tokens of %-player/offlineplayer%");
    }
    private Expression<OfflinePlayer> player;
    @Override
    protected Double[] get(@NotNull Event event) {
        OfflinePlayer p = player.getSingle(event);
        if (p != null)
            return new Double[] {TokenManager.getTokens(p.getUniqueId())};
        return null;
    }

    @Override
    public boolean isSingle() {
        return true;
    }

    @Override
    public @NotNull Class<? extends Double> getReturnType() {
        return Double.class;
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean init(Expression<?>[] exprs, int matchedPattern, @NotNull Kleenean isDelayed, SkriptParser.@NotNull ParseResult parser) {
        player = (Expression<OfflinePlayer>) exprs[0];
        return true;
    }

    @Override
    public @NotNull String toString(Event event, boolean b) {
        return "Expression with player: " + player.toString(event, b);
    }

    @Override
    public Class<?>[] acceptChange(final Changer.@NotNull ChangeMode mode) {
        if (mode == Changer.ChangeMode.REMOVE || mode == Changer.ChangeMode.SET || mode == Changer.ChangeMode.ADD) {
            return CollectionUtils.array(Double.class);
        }
        return null;
    }

    @Override
    public void change(@NotNull Event event, Object[] delta, Changer.@NotNull ChangeMode mode) {
        OfflinePlayer p = player.getSingle(event);
        if (p != null) {
            if (mode == Changer.ChangeMode.ADD) {
                TokenManager.giveTokens(p.getUniqueId(), (double) delta[0]);
            } else if (mode == Changer.ChangeMode.REMOVE) {
                TokenManager.removeTokens(p.getUniqueId(), (double) delta[0]);
            } else if (mode == Changer.ChangeMode.SET) {
                TokenManager.setTokens(p.getUniqueId(), (double) delta[0]);
            }
        }
    }

}
