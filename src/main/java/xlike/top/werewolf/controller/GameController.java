package xlike.top.werewolf.controller;
import cn.dev33.satoken.stp.StpUtil;
import xlike.top.werewolf.bean.dto.GameRoleDto;
import xlike.top.werewolf.bean.pojo.GameState;
import xlike.top.werewolf.bean.vo.ModelVo;
import xlike.top.werewolf.config.R;
import xlike.top.werewolf.service.GameService;
import xlike.top.werewolf.service.ModelService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;
import xlike.top.werewolf.utils.AesUtils;
import java.util.List;
import java.util.Map;

/**
 * 狼人杀游戏控制器，包含游戏流程控制和模型管理功能
 * @author xlike
 */
@RestController
@RequestMapping("/game")
@AllArgsConstructor
public class GameController {

    private final GameService gameService;
    private final ModelService modelService;

    /**
     * 分配游戏角色
     * 将前端传入的角色和AI模型对应关系存储到Redis中，并初始化游戏状态
     * @param gameRoleDtoList 角色分配列表，每个角色包含角色ID和对应的AI模型名称
     * @return 返回操作结果，成功则返回成功消息，失败则返回错误信息
     */
    @PostMapping("/distRole")
    public R<String> distRole(@RequestBody List<GameRoleDto> gameRoleDtoList) {
        return gameService.distRole(gameRoleDtoList);
    }
    /**
     * 获取当前分配的角色信息
     * 从Redis中读取角色分配数据，返回给前端展示
     * @return 返回角色分配列表，包含每个角色的ID和对应的AI模型名称
     */
    @GetMapping("/getRole")
    public R<List<GameRoleDto>> getRole() {
        return gameService.getRole();
    }
    /**
     * 开始游戏
     * 将游戏状态设置为第一天，正式启动游戏流程
     * @return 返回操作结果，成功则返回成功消息，失败则返回错误信息
     */
    @PostMapping("/startGame")
    public R<String> startGame() {
        return gameService.startGame();
    }
    /**
     * 进入夜晚阶段
     * 触发夜晚阶段的逻辑处理，包括狼人、预言家和女巫的行动，并返回详细的行动结果
     * @return 返回夜晚阶段的详细结果，包括每个角色的行动、目标和死亡信息
     */
    @PostMapping("/nightPhase")
    public R<Map<String, Object>> nightPhase() {
        return gameService.nightPhase();
    }
    /**
     * 进入白天阶段
     * 触发白天阶段的逻辑处理，包括玩家投票和猎人反击（若适用），并返回详细的行动结果
     * @return 返回白天阶段的详细结果，包括投票详情、死亡信息和猎人反击信息
     */
    @PostMapping("/dayPhase")
    public R<Map<String, Object>> dayPhase() {
        return gameService.dayPhase();
    }
    /**
     * 获取当前游戏状态
     * 从Redis中读取当前的游戏状态数据，包括天数、玩家存活情况和胜负结果
     * @return 返回游戏状态对象，包含当前天数、是否结束、胜利阵营等信息
     */
    @GetMapping("/getGameState")
    public R<GameState> getGameState() {
        return gameService.getGameState();
    }

    /**
     * 获取模型列表
     */
    @GetMapping("/models")
    public R<List<ModelVo>> getModelList(
            @RequestParam String key,
            @RequestParam(defaultValue = "https://api.openai.com/v1/models") String url) {
        if (url == null || url.isEmpty()) {
            return R.failed("url不能为空");
        }
        if (!url.contains("/v1/models")) {
            url = url + "/v1/models";
        }
        List<ModelVo> modelList = modelService.getModelList(key, url);
        if (modelList == null || modelList.isEmpty()) {
            return R.failed("获取模型列表失败或者为空");
        }
        String[] split = url.split("/v1/models");
        String newUrl = split[0];
        String encrypted = AesUtils.encryptTogether(newUrl, key);
        if(encrypted == null){
            return R.failed("业务异常，请联系管理员");
        }
        StpUtil.login(encrypted);
        return R.ok(modelList);
    }


    /**
     * 模型测活 true 为有效模型
     */
    @GetMapping("/model/test")
    public R<Boolean> testModel(@RequestParam String key,
                                @RequestParam(defaultValue = "https://api.openai.com/v1/chat/completions") String url,
                                @RequestParam String modelName) {
        return R.ok(modelService.modelTest(key, url, modelName));
    }



}
