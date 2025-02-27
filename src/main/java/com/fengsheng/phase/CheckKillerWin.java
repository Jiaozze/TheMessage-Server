package com.fengsheng.phase;

import com.fengsheng.Fsm;
import com.fengsheng.Game;
import com.fengsheng.Player;
import com.fengsheng.ResolveResult;
import com.fengsheng.card.Card;
import com.fengsheng.protos.Common;
import com.fengsheng.skill.SkillId;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 判断镇压者获胜条件，或者只剩一个人存活
 *
 * @param whoseTurn       谁的回合
 * @param diedQueue       死亡顺序
 * @param afterDieResolve 死亡结算后的下一个动作
 */
public record CheckKillerWin(Player whoseTurn, List<Player> diedQueue, Fsm afterDieResolve) implements Fsm {
    private static final Logger log = Logger.getLogger(CheckKillerWin.class);

    @Override
    public ResolveResult resolve() {
        if (diedQueue.isEmpty())
            return new ResolveResult(afterDieResolve, true);
        Player[] players = whoseTurn.getGame().getPlayers();
        Player killer = null;
        for (Player p : players) {
            if (p.getIdentity() == Common.color.Black && p.getSecretTask() == Common.secret_task.Killer) {
                if (!p.isLose()) killer = p;
                break;
            }
        }
        if (whoseTurn == killer) {
            for (Player whoDie : diedQueue) {
                int count = 0;
                for (Card card : whoDie.getMessageCards().values()) {
                    for (Common.color color : card.getColors()) {
                        if (color != Common.color.Black) {
                            count++;
                            break;
                        }
                    }
                }
                if (count >= 2) {
                    List<Player> winner = new ArrayList<>();
                    winner.add(killer);
                    if (killer.findSkill(SkillId.WEI_SHENG) != null && killer.isRoleFaceUp()) {
                        for (Player p : players) {
                            if (!p.isLose() && p.getIdentity() == Common.color.Has_No_Identity) {
                                winner.add(p);
                            }
                        }
                    }
                    Player[] winners = winner.toArray(new Player[0]);
                    log.info(killer + "宣告胜利，胜利者有" + Arrays.toString(winners));
                    for (Player p : players)
                        p.notifyWin(new Player[]{killer}, winners);
                    whoseTurn.getGame().end(winner);
                    return new ResolveResult(null, false);
                }
            }
        }
        Player alivePlayer = null;
        for (Player p : players) {
            if (p.isAlive()) {
                if (alivePlayer == null) {
                    alivePlayer = p;
                } else {
                    // 至少有2个人存活，游戏继续
                    return new ResolveResult(new DieSkill(whoseTurn, diedQueue, whoseTurn, afterDieResolve), true);
                }
            }
        }
        if (alivePlayer == null) {
            // 全部死亡，游戏结束
            log.info("全部死亡，游戏结束");
            for (Player p : players) {
                p.notifyWin(new Player[0], new Player[0]);
            }
            whoseTurn.getGame().end(Collections.emptyList());
            return new ResolveResult(null, false);
        }
        // 只剩1个人存活，游戏结束
        onlyOneAliveWinner(whoseTurn.getGame(), alivePlayer);
        return new ResolveResult(null, false);
    }

    /**
     * 只剩1个人存活，游戏结束
     */
    public static void onlyOneAliveWinner(Game g, Player alivePlayer) {
        Player[] players = g.getPlayers();
        List<Player> winner = new ArrayList<>();
        Common.color identity = alivePlayer.getIdentity();
        if (identity == Common.color.Red || identity == Common.color.Blue) {
            for (Player p : players) {
                if (!p.isLose() && identity == p.getIdentity()) {
                    winner.add(p);
                }
            }
        } else {
            winner.add(alivePlayer);
        }
        boolean hasGuXiaoMeng = false;
        for (Player p : winner) {
            if (p.findSkill(SkillId.WEI_SHENG) != null && p.isRoleFaceUp()) {
                hasGuXiaoMeng = true;
                break;
            }
        }
        if (hasGuXiaoMeng) {
            for (Player p : players) {
                if (!p.isLose() && p.getIdentity() == Common.color.Has_No_Identity) {
                    winner.add(p);
                }
            }
        }
        Player[] winners = winner.toArray(new Player[0]);
        log.info("只剩下" + alivePlayer + "存活，胜利者有" + Arrays.toString(winners));
        for (Player p : players) {
            p.notifyWin(new Player[0], winners);
        }
        g.end(winner);
    }
}
