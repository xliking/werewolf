package xlike.top.werewolf.bean.pojo;

import lombok.Data;
import java.util.List;

@Data
public class GameState {
    private int day;
    private boolean isGameOver;
    private String winnerCamp; // "狼人阵营" 或 "好人阵营"
    private List<PlayerState> players;
}
