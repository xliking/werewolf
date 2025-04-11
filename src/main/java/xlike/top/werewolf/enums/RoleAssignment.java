package xlike.top.werewolf.enums;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RoleAssignment {
    public static void main(String[] args) {
        // 定义6人局角色配置
        List<RoleEnum> roles = new ArrayList<>();
        roles.add(RoleEnum.WEREWOLF_1); // 2狼
        roles.add(RoleEnum.WEREWOLF_2);
        roles.add(RoleEnum.SEER);     // 1预言家
        roles.add(RoleEnum.WITCH);    // 1女巫
        roles.add(RoleEnum.HUNTER);   // 1猎人
        roles.add(RoleEnum.VILLAGER); // 1村民

        // 随机打乱角色
        Collections.shuffle(roles);

        // 模拟6名玩家
        String[] players = {"玩家1", "玩家2", "玩家3", "玩家4", "玩家5", "玩家6"};

        // 输出角色分配结果
        System.out.println("六人局角色分配如下：");
        for (int i = 0; i < players.length; i++) {
            System.out.println(players[i] + ": " + roles.get(i).getName() + " (" + roles.get(i).getCamp() + ")");
        }
    }
}
