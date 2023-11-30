package me.jadenp.nottokenspremium.Skripts;

import ch.njol.skript.Skript;
import ch.njol.skript.classes.Changer;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.ExpressionType;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import ch.njol.util.coll.CollectionUtils;
import me.jadenp.nottokenspremium.TokenManager;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.jetbrains.annotations.NotNull;

public class SkriptTokens extends SimpleExpression<Double> {

    static {
        Skript.registerExpression(SkriptTokens.class, Double.class, ExpressionType.COMBINED, "%tokens%");
    }
    private Expression<Player> player;
    @Override
    protected Double[] get(@NotNull Event event) {
        Player p = player.getSingle(event);
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
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, SkriptParser.ParseResult parser) {
        player = (Expression<Player>) exprs[0];
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
    public void change(Event event, Object[] delta, Changer.ChangeMode mode) {
        Player p = player.getSingle(event);
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
