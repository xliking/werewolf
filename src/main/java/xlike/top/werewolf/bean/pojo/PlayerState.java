package xlike.top.werewolf.bean.pojo;

import lombok.Data;

@Data
public class PlayerState {
    private String roleId; // 对应 RoleEnum 的 id
    private String roleAI; // 对应的 AI 模型
    private boolean isAlive;
    private String nightTarget; // 夜晚行动目标，空字符串表示无目标
    private boolean isSaved; // 是否被女巫救
    private boolean isPoisoned; // 是否被女巫毒
    private boolean witchSaveUsed; // 女巫是否用过解药
    private boolean witchPoisonUsed; // 女巫是否用过毒药
}
