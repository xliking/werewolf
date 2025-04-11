package xlike.top.werewolf.enums;

import lombok.Getter;

/**
 * 狼人杀游戏角色枚举类（适合6人局）
 * @author xlike
 */
@Getter
public enum RoleEnum {
    /**
     * 狼人1：夜晚可以与其他狼人一起选择一名玩家进行击杀。
     */
    WEREWOLF_1("狼人1", "夜晚可以与其他狼人一起选择一名玩家进行击杀", "狼人阵营", 1),

    /**
     * 狼人2：夜晚可以与其他狼人一起选择一名玩家进行击杀。
     */
    WEREWOLF_2("狼人2", "夜晚可以与其他狼人一起选择一名玩家进行击杀", "狼人阵营", 2),

    /**
     * 村民：没有特殊技能，依靠推理和投票驱逐狼人。
     */
    VILLAGER("村民", "没有特殊技能，依靠推理和投票驱逐狼人", "好人阵营", 3),

    /**
     * 预言家：每晚可以查验一名玩家的身份（狼人或好人）。
     */
    SEER("预言家", "每晚可以查验一名玩家的身份（狼人或好人）", "好人阵营", 4),

    /**
     * 女巫：拥有两瓶药水，分别为解药和毒药。解药可以救人，毒药可以毒杀一名玩家。
     */
    WITCH("女巫", "拥有解药和毒药，解药可以救人，毒药可以毒杀一名玩家", "好人阵营", 5),

    /**
     * 猎人：死亡时可以开枪带走一名玩家（可以选择不开枪）。
     */
    HUNTER("猎人", "死亡时可以开枪带走一名玩家（可以选择不开枪）", "好人阵营", 6);

    /**
     * 角色名称
     */
    private final String name;

    /**
     * 角色描述
     */
    private final String description;

    /**
     * 阵营
     */
    private final String camp;

    /**
     * 角色ID
     */
    private final int id;

    RoleEnum(String name, String description, String camp, int id) {
        this.name = name;
        this.description = description;
        this.camp = camp;
        this.id = id;
    }

    @Override
    public String toString() {
        return "ID: " + id + " | " + name + " - " + description + " (" + camp + ")";
    }

    /**
     * 测试枚举
     */
    public static void main(String[] args) {
        for (RoleEnum role : RoleEnum.values()) {
            System.out.println(role);
        }
    }
}
