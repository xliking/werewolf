package xlike.top.werewolf.service;

import xlike.top.werewolf.bean.dto.GameRoleDto;
import xlike.top.werewolf.bean.pojo.GameState;
import xlike.top.werewolf.bean.pojo.PlayerState;
import xlike.top.werewolf.common.PromptCommon;
import xlike.top.werewolf.config.R;
import xlike.top.werewolf.enums.RoleEnum;
import xlike.top.werewolf.utils.AesUtils;
import xlike.top.werewolf.utils.ChatUtils;
import xlike.top.werewolf.utils.RedisUtil;
import cn.dev33.satoken.stp.StpUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;

@Slf4j
@Service
public class GameService {

    private static final String GAME_ROLE_KEY = "game:role";
    private static final String GAME_STATE_KEY = "game:state";
    private static final int MIN_PLAYER_COUNT = 6;

    /**
     * 获取登录ID
     */
    private String getLoginId() {
        try {
            return StpUtil.getLoginIdAsString();
        } catch (Exception e) {
            log.error("获取登录ID失败，异常信息：{}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 获取API URL和Key
     */
    private Map<String, String> getApiCredentials(String loginId) {
        Map<String, String> credentials = new HashMap<>();
        credentials.put("apiUrl", "");
        credentials.put("apiKey", "");

        if (loginId == null) {
            log.error("无法获取登录ID，可能用户未登录或系统异常");
            return credentials;
        }

        String[] strings = AesUtils.decryptTogether(loginId);
        if (strings == null || strings.length != 2) {
            log.error("解密失败，请检查登录ID是否正确");
            return credentials;
        }

        credentials.put("apiUrl", strings[0]);
        credentials.put("apiKey", strings[1]);
        log.info("解密后的URL：{}, 解密后的Key: {}", strings[0], strings[1]);
        return credentials;
    }

    /**
     * 分配游戏角色
     */
    public R<String> distRole(List<GameRoleDto> gameRoleDtoList) {
        if (gameRoleDtoList == null || gameRoleDtoList.size() < MIN_PLAYER_COUNT) {
            log.warn("角色分配失败，人数不足。当前人数：{}", gameRoleDtoList == null ? 0 : gameRoleDtoList.size());
            return R.failed("人数分配不足，开始游戏至少需要" + MIN_PLAYER_COUNT + "名成员参与");
        }
        String urlKey = getLoginId();
        if (urlKey == null) {
            log.error("角色分配失败，无法获取登录ID，可能用户未登录或系统异常");
            return R.failed("请先登录或获取模型列表");
        }
        try {
            String redisKey = GAME_ROLE_KEY + ":" + urlKey;
            RedisUtil.set(redisKey, gameRoleDtoList);
            initializeGameState(urlKey, gameRoleDtoList);
            log.info("角色分配成功，Redis Key：{}", redisKey);
            return R.ok("角色分配成功");
        } catch (Exception e) {
            log.error("角色分配失败，存储到Redis时发生异常，urlKey：{}，异常信息：{}", urlKey, e.getMessage(), e);
            return R.failed("角色分配失败，系统异常");
        }
    }

    /**
     * 初始化游戏状态
     */
    private void initializeGameState(String urlKey, List<GameRoleDto> gameRoleDtoList) {
        List<PlayerState> playerStates = new ArrayList<>();
        for (GameRoleDto dto : gameRoleDtoList) {
            PlayerState state = new PlayerState();
            state.setRoleId(dto.getRoleId());
            state.setRoleAI(dto.getRoleAI());
            state.setAlive(true);
            state.setNightTarget("");
            state.setSaved(false);
            state.setPoisoned(false);
            state.setWitchSaveUsed(false);
            state.setWitchPoisonUsed(false);
            playerStates.add(state);
        }
        GameState gameState = new GameState();
        gameState.setDay(0);
        gameState.setGameOver(false);
        gameState.setWinnerCamp("");
        gameState.setPlayers(playerStates);
        RedisUtil.set(GAME_STATE_KEY + ":" + urlKey, gameState);
    }

    /**
     * 获取角色信息
     */
    public R<List<GameRoleDto>> getRole() {
        String urlKey = getLoginId();
        if (urlKey == null) {
            log.error("无法获取登录ID，可能用户未登录或系统异常");
            return R.failed("请先登录或获取模型列表");
        }
        String redisKey = GAME_ROLE_KEY + ":" + urlKey;
        Object object = RedisUtil.get(redisKey);
        if (object == null) {
            log.error("获取角色失败，Redis Key：{}，可能Redis中不存在该Key", redisKey);
            return R.failed("获取角色失败，请重新设置");
        }
        List<GameRoleDto> gameRoleDtoList = (List<GameRoleDto>) object;
        return R.ok(gameRoleDtoList);
    }

    /**
     * 开始游戏
     */
    public R<String> startGame() {
        String urlKey = getLoginId();
        if (urlKey == null) {
            return R.failed("请先登录");
        }
        String roleKey = GAME_ROLE_KEY + ":" + urlKey;
        if (RedisUtil.get(roleKey) == null) {
            return R.failed("请先分配角色");
        }
        String stateKey = GAME_STATE_KEY + ":" + urlKey;
        GameState gameState = (GameState) RedisUtil.get(stateKey);
        if (gameState == null) {
            return R.failed("游戏状态异常，请重新分配角色");
        }
        gameState.setDay(1);
        RedisUtil.set(stateKey, gameState);
        log.info("游戏开始，当前天数：第 1 天");
        return R.ok("游戏开始");
    }

    /**
     * 进入夜晚阶段
     */
    public R<Map<String, Object>> nightPhase() {
        String urlKey = getLoginId();
        if (urlKey == null) {
            return R.failed("请先登录");
        }
        String stateKey = GAME_STATE_KEY + ":" + urlKey;
        GameState gameState = (GameState) RedisUtil.get(stateKey);
        if (gameState == null || gameState.isGameOver()) {
            return R.failed("游戏未开始或已结束");
        }
        // 重置夜晚状态
        for (PlayerState ps : gameState.getPlayers()) {
            ps.setNightTarget("");
            ps.setSaved(false);
            ps.setPoisoned(false);
        }
        // 夜晚行动结果
        Map<String, Object> nightResults = new HashMap<>();
        nightResults.put("day", gameState.getDay());
        nightResults.put("phase", "夜晚");

        // 狼人行动
        Map<String, String> werewolfResult = werewolfAction(gameState);
        nightResults.put("werewolfAction", werewolfResult);

        // 预言家行动
        Map<String, String> seerResult = seerAction(gameState);
        nightResults.put("seerAction", seerResult);

        // 女巫行动
        Map<String, Object> witchResult = witchAction(gameState);
        nightResults.put("witchAction", witchResult);

        // 处理夜晚结果并记录死亡信息
        List<String> deaths = processNightResults(gameState);
        nightResults.put("deaths", deaths);

        RedisUtil.set(stateKey, gameState);
        log.info("夜晚阶段结束，第 {} 天", gameState.getDay());
        return R.ok(nightResults);
    }

    /**
     * 狼人行动逻辑
     */
    private Map<String, String> werewolfAction(GameState gameState) {
        Map<String, String> result = new HashMap<>();
        result.put("action", "无行动");
        result.put("target", "");
        result.put("reason", "");

        List<PlayerState> werewolves = gameState.getPlayers().stream()
                .filter(p -> isWerewolf(p.getRoleId()) && p.isAlive())
                .toList();
        if (werewolves.isEmpty()) {
            result.put("reason", "没有存活的狼人");
            return result;
        }

        PlayerState leadWolf = werewolves.getFirst();
        String prompt = PromptCommon.werewolfPrompt
                .replace("{roleId}", leadWolf.getRoleId())
                .replace("{day}", String.valueOf(gameState.getDay()))
                .replace("{alivePlayers}", getAlivePlayersString(gameState));
        try {
            String loginId = getLoginId();
            Map<String, String> credentials = getApiCredentials(loginId);
            if (credentials.get("apiUrl").isEmpty() || credentials.get("apiKey").isEmpty()) {
                log.error("获取API URL或Key失败，无法调用狼人AI");
                randomWerewolfTarget(gameState, werewolves);
                result.put("action", "随机选择目标");
                result.put("target", werewolves.get(0).getNightTarget());
                result.put("reason", "API调用失败，随机选择");
                return result;
            }
            String apiUrl = credentials.get("apiUrl");
            String apiKey = credentials.get("apiKey");
            String response = ChatUtils.sendChatRequest(apiUrl, apiKey, leadWolf.getRoleAI(), leadWolf.getRoleId(), prompt);
            String targetId = parseTargetIdFromResponse(response);
            if (targetId != null && isValidTarget(gameState, targetId)) {
                for (PlayerState wolf : werewolves) {
                    wolf.setNightTarget(targetId);
                }
                log.info("狼人选择了杀死玩家 {}", targetId);
                result.put("action", "选择击杀目标");
                result.put("target", targetId);
                result.put("reason", extractReasonFromResponse(response));
            } else {
                log.warn("狼人 AI 返回无效目标，随机选择");
                randomWerewolfTarget(gameState, werewolves);
                result.put("action", "随机选择目标");
                result.put("target", werewolves.getFirst().getNightTarget());
                result.put("reason", "AI返回无效目标，随机选择");
            }
        } catch (IOException e) {
            log.error("调用狼人 AI 失败：{}", e.getMessage());
            randomWerewolfTarget(gameState, werewolves);
            result.put("action", "随机选择目标");
            result.put("target", werewolves.getFirst().getNightTarget());
            result.put("reason", "API调用失败，随机选择");
        }
        return result;
    }

    /**
     * 预言家行动逻辑
     */
    private Map<String, String> seerAction(GameState gameState) {
        Map<String, String> result = new HashMap<>();
        result.put("action", "无行动");
        result.put("target", "");
        result.put("reason", "");
        result.put("identity", "");

        PlayerState seer = gameState.getPlayers().stream()
                .filter(p -> p.getRoleId().equals(String.valueOf(RoleEnum.SEER.getId())) && p.isAlive())
                .findFirst().orElse(null);
        if (seer == null) {
            result.put("reason", "没有存活的预言家");
            return result;
        }

        String prompt = PromptCommon.seerPrompt
                .replace("{roleId}", seer.getRoleId())
                .replace("{day}", String.valueOf(gameState.getDay()))
                .replace("{alivePlayers}", getAlivePlayersString(gameState));
        try {
            String loginId = getLoginId();
            Map<String, String> credentials = getApiCredentials(loginId);
            if (credentials.get("apiUrl").isEmpty() || credentials.get("apiKey").isEmpty()) {
                log.error("获取API URL或Key失败，无法调用预言家AI");
                randomSeerTarget(gameState, seer);
                result.put("action", "随机查验目标");
                result.put("target", seer.getNightTarget());
                result.put("reason", "API调用失败，随机选择");
                result.put("identity", isWerewolf(seer.getNightTarget()) ? "狼人" : "好人");
                return result;
            }
            String apiUrl = credentials.get("apiUrl");
            String apiKey = credentials.get("apiKey");
            String response = ChatUtils.sendChatRequest(apiUrl, apiKey, seer.getRoleAI(), seer.getRoleId(), prompt);
            String targetId = parseTargetIdFromResponse(response);
            if (targetId != null && isValidTarget(gameState, targetId)) {
                seer.setNightTarget(targetId);
                String identity = isWerewolf(targetId) ? "狼人" : "好人";
                log.info("预言家查验了玩家 {}，身份是：{}", targetId, identity);
                result.put("action", "查验身份");
                result.put("target", targetId);
                result.put("reason", extractReasonFromResponse(response));
                result.put("identity", identity);
            } else {
                log.warn("预言家 AI 返回无效目标，随机选择");
                randomSeerTarget(gameState, seer);
                result.put("action", "随机查验目标");
                result.put("target", seer.getNightTarget());
                result.put("reason", "AI返回无效目标，随机选择");
                result.put("identity", isWerewolf(seer.getNightTarget()) ? "狼人" : "好人");
            }
        } catch (IOException e) {
            log.error("调用预言家 AI 失败：{}", e.getMessage());
            randomSeerTarget(gameState, seer);
            result.put("action", "随机查验目标");
            result.put("target", seer.getNightTarget());
            result.put("reason", "API调用失败，随机选择");
            result.put("identity", isWerewolf(seer.getNightTarget()) ? "狼人" : "好人");
        }
        return result;
    }

    /**
     * 女巫行动逻辑
     */
    private Map<String, Object> witchAction(GameState gameState) {
        Map<String, Object> result = new HashMap<>();
        result.put("action", "无行动");
        result.put("save", false);
        result.put("saveTarget", "");
        result.put("poison", false);
        result.put("poisonTarget", "");
        result.put("reason", "");

        PlayerState witch = gameState.getPlayers().stream()
                .filter(p -> p.getRoleId().equals(String.valueOf(RoleEnum.WITCH.getId())) && p.isAlive())
                .findFirst().orElse(null);
        if (witch == null) {
            result.put("reason", "没有存活的女巫");
            return result;
        }

        String killedPlayer = getKilledPlayerId(gameState);
        if (killedPlayer.isEmpty()) {
            killedPlayer = "无人被击杀";
        }
        String prompt = PromptCommon.witchPrompt
                .replace("{roleId}", witch.getRoleId())
                .replace("{day}", String.valueOf(gameState.getDay()))
                .replace("{saveUsed}", witch.isWitchSaveUsed() ? "已使用" : "未使用")
                .replace("{poisonUsed}", witch.isWitchPoisonUsed() ? "已使用" : "未使用")
                .replace("{alivePlayers}", getAlivePlayersString(gameState))
                .replace("{killedPlayer}", killedPlayer);
        try {
            String loginId = getLoginId();
            Map<String, String> credentials = getApiCredentials(loginId);
            if (credentials.get("apiUrl").isEmpty() || credentials.get("apiKey").isEmpty()) {
                log.error("获取API URL或Key失败，无法调用女巫AI");
                randomWitchAction(gameState, witch);
                result.put("action", "随机行动");
                result.put("save", witch.isWitchSaveUsed());
                result.put("saveTarget", witch.isWitchSaveUsed() ? getKilledPlayerId(gameState) : "");
                result.put("poison", witch.isWitchPoisonUsed());
                result.put("poisonTarget", witch.isWitchPoisonUsed() ? findPoisonedPlayer(gameState) : "");
                result.put("reason", "API调用失败，随机选择");
                return result;
            }
            String apiUrl = credentials.get("apiUrl");
            String apiKey = credentials.get("apiKey");
            String response = ChatUtils.sendChatRequest(apiUrl, apiKey, witch.getRoleAI(), witch.getRoleId(), prompt);
            Map<String, String> actions = parseWitchActionsFromResponse(response);
            if ("是".equals(actions.get("save")) && !witch.isWitchSaveUsed()) {
                String saveTarget = actions.get("saveTarget");
                if (saveTarget.equals(getKilledPlayerId(gameState))) {
                    PlayerState killed = getPlayerById(gameState, saveTarget);
                    if (killed != null) {
                        killed.setSaved(true);
                        witch.setWitchSaveUsed(true);
                        log.info("女巫救了玩家 {}", saveTarget);
                        result.put("save", true);
                        result.put("saveTarget", saveTarget);
                    }
                }
            }
            if ("是".equals(actions.get("poison")) && !witch.isWitchPoisonUsed()) {
                String poisonTarget = actions.get("poisonTarget");
                if (isValidTarget(gameState, poisonTarget)) {
                    PlayerState target = getPlayerById(gameState, poisonTarget);
                    if (target != null) {
                        target.setPoisoned(true);
                        witch.setWitchPoisonUsed(true);
                        log.info("女巫毒死了玩家 {}", poisonTarget);
                        result.put("poison", true);
                        result.put("poisonTarget", poisonTarget);
                    }
                }
            }
            result.put("action", "执行行动");
            result.put("reason", extractReasonFromResponse(response));
        } catch (IOException e) {
            log.error("调用女巫 AI 失败：{}", e.getMessage());
            randomWitchAction(gameState, witch);
            result.put("action", "随机行动");
            result.put("save", witch.isWitchSaveUsed());
            result.put("saveTarget", witch.isWitchSaveUsed() ? getKilledPlayerId(gameState) : "");
            result.put("poison", witch.isWitchPoisonUsed());
            result.put("poisonTarget", witch.isWitchPoisonUsed() ? findPoisonedPlayer(gameState) : "");
            result.put("reason", "API调用失败，随机选择");
        }
        return result;
    }

    /**
     * 处理夜晚结果
     */
    private List<String> processNightResults(GameState gameState) {
        List<String> deaths = new ArrayList<>();
        for (PlayerState p : gameState.getPlayers()) {
            if (p.isPoisoned()) {
                p.setAlive(false);
                deaths.add("玩家 " + p.getRoleId() + " 被女巫毒死");
                log.info("玩家 {} 被女巫毒死！", p.getRoleId());
            } else if (p.getRoleId().equals(getKilledPlayerId(gameState)) && !p.isSaved()) {
                p.setAlive(false);
                deaths.add("玩家 " + p.getRoleId() + " 被狼人杀死");
                log.info("玩家 {} 被狼人杀死！", p.getRoleId());
            }
        }
        checkGameOver(gameState);
        return deaths;
    }

    /**
     * 进入白天阶段
     */
    public R<Map<String, Object>> dayPhase() {
        String urlKey = getLoginId();
        if (urlKey == null) {
            return R.failed("请先登录");
        }
        String stateKey = GAME_STATE_KEY + ":" + urlKey;
        GameState gameState = (GameState) RedisUtil.get(stateKey);
        if (gameState == null || gameState.isGameOver()) {
            return R.failed("游戏未开始或已结束");
        }
        // 白天行动结果
        Map<String, Object> dayResults = new HashMap<>();
        dayResults.put("day", gameState.getDay());
        dayResults.put("phase", "白天");

        // 投票环节
        Map<String, Object> voteResults = votePhase(gameState);
        dayResults.put("voteResults", voteResults);

        gameState.setDay(gameState.getDay() + 1);
        RedisUtil.set(stateKey, gameState);
        log.info("白天阶段结束，第 {} 天", gameState.getDay());
        return R.ok(dayResults);
    }

    /**
     * 投票环节
     */
    private Map<String, Object> votePhase(GameState gameState) {
        Map<String, Object> voteResults = new HashMap<>();
        List<Map<String, String>> individualVotes = new ArrayList<>();
        List<String> deaths = new ArrayList<>();

        List<PlayerState> alivePlayers = gameState.getPlayers().stream()
                .filter(PlayerState::isAlive)
                .toList();
        if (alivePlayers.isEmpty()) {
            voteResults.put("summary", "没有存活玩家，无法投票");
            voteResults.put("votes", individualVotes);
            voteResults.put("deaths", deaths);
            return voteResults;
        }

        Map<String, Integer> voteCount = new HashMap<>();
        for (PlayerState voter : alivePlayers) {
            Map<String, String> voteDetail = new HashMap<>();
            voteDetail.put("voterId", voter.getRoleId());
            String roleName = Arrays.stream(RoleEnum.values())
                    .filter(r -> String.valueOf(r.getId()).equals(voter.getRoleId()))
                    .map(RoleEnum::getName)
                    .findFirst().orElse("未知角色");
            String camp = Arrays.stream(RoleEnum.values())
                    .filter(r -> String.valueOf(r.getId()).equals(voter.getRoleId()))
                    .map(RoleEnum::getCamp)
                    .findFirst().orElse("未知阵营");
            String prompt = PromptCommon.votePrompt
                    .replace("{roleName}", roleName)
                    .replace("{roleId}", voter.getRoleId())
                    .replace("{day}", String.valueOf(gameState.getDay()))
                    .replace("{alivePlayers}", getAlivePlayersString(gameState))
                    .replace("{lastNightEvents}", getLastNightEvents(gameState))
                    .replace("{camp}", camp);
            try {
                String loginId = getLoginId();
                Map<String, String> credentials = getApiCredentials(loginId);
                if (credentials.get("apiUrl").isEmpty() || credentials.get("apiKey").isEmpty()) {
                    log.error("获取API URL或Key失败，无法调用投票AI");
                    randomVote(gameState, voter, voteCount);
                    voteDetail.put("targetId", voter.getNightTarget());
                    voteDetail.put("reason", "API调用失败，随机投票");
                    individualVotes.add(voteDetail);
                    continue;
                }
                String apiUrl = credentials.get("apiUrl");
                String apiKey = credentials.get("apiKey");
                String response = ChatUtils.sendChatRequest(apiUrl, apiKey, voter.getRoleAI(), voter.getRoleId(), prompt);
                String targetId = parseTargetIdFromResponse(response);
                if (targetId != null && isValidTarget(gameState, targetId)) {
                    voteCount.merge(targetId, 1, Integer::sum);
                    log.info("玩家 {} 投票给 {}", voter.getRoleId(), targetId);
                    voteDetail.put("targetId", targetId);
                    voteDetail.put("reason", extractReasonFromResponse(response));
                } else {
                    log.warn("玩家 {} AI 返回无效目标，随机投票", voter.getRoleId());
                    randomVote(gameState, voter, voteCount);
                    voteDetail.put("targetId", voter.getNightTarget());
                    voteDetail.put("reason", "AI返回无效目标，随机投票");
                }
            } catch (IOException e) {
                log.error("调用玩家 {} AI 投票失败：{}", voter.getRoleId(), e.getMessage());
                randomVote(gameState, voter, voteCount);
                voteDetail.put("targetId", voter.getNightTarget());
                voteDetail.put("reason", "API调用失败，随机投票");
            }
            individualVotes.add(voteDetail);
        }
        // 统计投票结果
        String votedOutId = getMostVotedPlayer(voteCount);
        if (votedOutId != null) {
            PlayerState votedOut = getPlayerById(gameState, votedOutId);
            if (votedOut != null) {
                votedOut.setAlive(false);
                deaths.add("玩家 " + votedOut.getRoleId() + " 被投票出局");
                log.info("玩家 {} 被投票出局！", votedOut.getRoleId());
                // 猎人反击技能
                if (votedOut.getRoleId().equals(String.valueOf(RoleEnum.HUNTER.getId()))) {
                    Map<String, String> hunterResult = hunterCounterattack(gameState, votedOut);
                    voteResults.put("hunterCounterattack", hunterResult);
                    if (hunterResult.get("targetId") != null && !hunterResult.get("targetId").isEmpty()) {
                        deaths.add("玩家 " + hunterResult.get("targetId") + " 被猎人反击带走");
                    }
                }
            }
        }
        checkGameOver(gameState);
        voteResults.put("summary", votedOutId != null ? "玩家 " + votedOutId + " 被投票出局" : "无玩家被投票出局");
        voteResults.put("votes", individualVotes);
        voteResults.put("deaths", deaths);
        return voteResults;
    }

    /**
     * 猎人临死反击
     */
    private Map<String, String> hunterCounterattack(GameState gameState, PlayerState hunter) {
        Map<String, String> result = new HashMap<>();
        result.put("action", "无反击");
        result.put("targetId", "");
        result.put("reason", "");

        String prompt = PromptCommon.hunterCounterattackPrompt
                .replace("{roleId}", hunter.getRoleId())
                .replace("{day}", String.valueOf(gameState.getDay()))
                .replace("{alivePlayers}", getAlivePlayersString(gameState));
        try {
            String loginId = getLoginId();
            Map<String, String> credentials = getApiCredentials(loginId);
            if (credentials.get("apiUrl").isEmpty() || credentials.get("apiKey").isEmpty()) {
                log.error("获取API URL或Key失败，无法调用猎人AI");
                randomHunterCounterattack(gameState, hunter);
                result.put("action", "随机反击");
                result.put("targetId", findHunterCounterattackTarget(gameState, hunter));
                result.put("reason", "API调用失败，随机选择");
                return result;
            }
            String apiUrl = credentials.get("apiUrl");
            String apiKey = credentials.get("apiKey");
            String response = ChatUtils.sendChatRequest(apiUrl, apiKey, hunter.getRoleAI(), hunter.getRoleId(), prompt);
            Map<String, String> actions = parseHunterCounterattackFromResponse(response);
            if ("是".equals(actions.get("shoot"))) {
                String targetId = actions.get("targetId");
                if (isValidTarget(gameState, targetId)) {
                    PlayerState target = getPlayerById(gameState, targetId);
                    if (target != null) {
                        target.setAlive(false);
                        log.info("猎人临死反击，带走了玩家 {}", targetId);
                        result.put("action", "反击");
                        result.put("targetId", targetId);
                        result.put("reason", extractReasonFromResponse(response));
                    }
                }
            }
        } catch (IOException e) {
            log.error("调用猎人 AI 反击失败：{}", e.getMessage());
            randomHunterCounterattack(gameState, hunter);
            result.put("action", "随机反击");
            result.put("targetId", findHunterCounterattackTarget(gameState, hunter));
            result.put("reason", "API调用失败，随机选择");
        }
        return result;
    }

    /**
     * 获取当前游戏状态
     */
    public R<GameState> getGameState() {
        String urlKey = getLoginId();
        if (urlKey == null) {
            return R.failed("请先登录");
        }
        String stateKey = GAME_STATE_KEY + ":" + urlKey;
        GameState gameState = (GameState) RedisUtil.get(stateKey);
        if (gameState == null) {
            return R.failed("游戏未开始或状态异常");
        }
        return R.ok(gameState);
    }

    /**
     * 检查游戏是否结束
     */
    private void checkGameOver(GameState gameState) {
        int werewolfCount = 0;
        int goodGuyCount = 0;
        for (PlayerState p : gameState.getPlayers()) {
            if (p.isAlive()) {
                if (isWerewolf(p.getRoleId())) {
                    werewolfCount++;
                } else {
                    goodGuyCount++;
                }
            }
        }
        if (werewolfCount == 0) {
            gameState.setGameOver(true);
            gameState.setWinnerCamp("好人阵营");
            log.info("好人阵营胜利！所有狼人被消灭！");
        } else if (werewolfCount >= goodGuyCount) {
            gameState.setGameOver(true);
            gameState.setWinnerCamp("狼人阵营");
            log.info("狼人阵营胜利！狼人数量大于等于好人数量！");
        }
    }

    /**
     * 辅助方法：是否为狼人
     */
    private boolean isWerewolf(String roleId) {
        return roleId.equals(String.valueOf(RoleEnum.WEREWOLF_1.getId())) ||
                roleId.equals(String.valueOf(RoleEnum.WEREWOLF_2.getId()));
    }

    /**
     * 辅助方法：获取存活玩家列表字符串
     */
    private String getAlivePlayersString(GameState gameState) {
        StringBuilder sb = new StringBuilder();
        for (PlayerState p : gameState.getPlayers()) {
            if (p.isAlive()) {
                sb.append("ID: ").append(p.getRoleId()).append(" (未知身份)\n");
            }
        }
        return sb.toString();
    }

    /**
     * 辅助方法：获取昨晚事件记录
     */
    private String getLastNightEvents(GameState gameState) {
        StringBuilder sb = new StringBuilder();
        for (PlayerState p : gameState.getPlayers()) {
            if (!p.isAlive()) {
                if (p.isPoisoned()) {
                    sb.append("玩家 ").append(p.getRoleId()).append(" 被毒死；");
                } else if (p.getRoleId().equals(getKilledPlayerId(gameState)) && !p.isSaved()) {
                    sb.append("玩家 ").append(p.getRoleId()).append(" 被狼人杀死；");
                }
            }
        }
        return !sb.isEmpty() ? sb.toString() : "昨晚无人死亡";
    }

    /**
     * 辅助方法：获取狼人目标
     */
    private String getKilledPlayerId(GameState gameState) {
        for (PlayerState p : gameState.getPlayers()) {
            if (isWerewolf(p.getRoleId()) && p.isAlive() && !p.getNightTarget().isEmpty()) {
                return p.getNightTarget();
            }
        }
        return "";
    }

    /**
     * 辅助方法：根据ID获取玩家
     */
    private PlayerState getPlayerById(GameState gameState, String roleId) {
        return gameState.getPlayers().stream()
                .filter(p -> p.getRoleId().equals(roleId))
                .findFirst()
                .orElse(null);
    }

    /**
     * 辅助方法：验证目标是否有效
     */
    private boolean isValidTarget(GameState gameState, String targetId) {
        return gameState.getPlayers().stream()
                .anyMatch(p -> p.getRoleId().equals(targetId) && p.isAlive());
    }

    /**
     * 辅助方法：解析 AI 响应中的目标ID
     */
    private String parseTargetIdFromResponse(String response) {
        try {
            String[] lines = response.split("\n");
            for (String line : lines) {
                if (line.startsWith("目标ID: ")) {
                    return line.replace("目标ID: ", "").trim();
                }
            }
        } catch (Exception e) {
            log.error("解析目标ID失败：{}", response);
        }
        return null;
    }

    /**
     * 辅助方法：从响应中提取理由
     */
    private String extractReasonFromResponse(String response) {
        try {
            String[] lines = response.split("\n");
            for (String line : lines) {
                if (line.startsWith("理由: ")) {
                    return line.replace("理由: ", "").trim();
                }
            }
        } catch (Exception e) {
            log.error("解析理由失败：{}", response);
        }
        return "无理由";
    }

    /**
     * 辅助方法：解析女巫行动
     */
    private Map<String, String> parseWitchActionsFromResponse(String response) {
        Map<String, String> actions = new HashMap<>();
        actions.put("save", "否");
        actions.put("saveTarget", "");
        actions.put("poison", "否");
        actions.put("poisonTarget", "");
        try {
            String[] lines = response.split("\n");
            for (String line : lines) {
                if (line.startsWith("是否救人: ")) {
                    actions.put("save", line.replace("是否救人: ", "").trim());
                }
                if (line.startsWith("救人目标ID: ")) {
                    actions.put("saveTarget", line.replace("救人目标ID: ", "").trim());
                }
                if (line.startsWith("是否毒人: ")) {
                    actions.put("poison", line.replace("是否毒人: ", "").trim());
                }
                if (line.startsWith("毒人目标ID: ")) {
                    actions.put("poisonTarget", line.replace("毒人目标ID: ", "").trim());
                }
            }
        } catch (Exception e) {
            log.error("解析女巫行动失败：{}", response);
        }
        return actions;
    }

    /**
     * 辅助方法：解析猎人反击响应
     */
    private Map<String, String> parseHunterCounterattackFromResponse(String response) {
        Map<String, String> actions = new HashMap<>();
        actions.put("shoot", "否");
        actions.put("targetId", "");
        try {
            String[] lines = response.split("\n");
            for (String line : lines) {
                if (line.startsWith("是否开枪: ")) {
                    actions.put("shoot", line.replace("是否开枪: ", "").trim());
                }
                if (line.startsWith("目标ID: ")) {
                    actions.put("targetId", line.replace("目标ID: ", "").trim());
                }
            }
        } catch (Exception e) {
            log.error("解析猎人反击行动失败：{}", response);
        }
        return actions;
    }

    /**
     * 辅助方法：统计投票最多的玩家
     */
    private String getMostVotedPlayer(Map<String, Integer> voteCount) {
        return voteCount.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    /**
     * 辅助方法：查找被毒死的玩家
     */
    private String findPoisonedPlayer(GameState gameState) {
        return gameState.getPlayers().stream()
                .filter(PlayerState::isPoisoned)
                .map(PlayerState::getRoleId)
                .findFirst()
                .orElse("");
    }

    /**
     * 辅助方法：查找猎人反击目标
     */
    private String findHunterCounterattackTarget(GameState gameState, PlayerState hunter) {
        return gameState.getPlayers().stream()
                .filter(p -> !p.isAlive() && !p.getRoleId().equals(hunter.getRoleId()))
                .map(PlayerState::getRoleId)
                .findFirst()
                .orElse("");
    }

    /**
     * 辅助方法：随机选择目标 (AI 调用失败时的备用逻辑)
     */
    private void randomWerewolfTarget(GameState gameState, List<PlayerState> werewolves) {
        List<PlayerState> aliveGoodPlayers = gameState.getPlayers().stream()
                .filter(p -> p.isAlive() && !isWerewolf(p.getRoleId()))
                .toList();
        if (!aliveGoodPlayers.isEmpty()) {
            PlayerState target = aliveGoodPlayers.get(new Random().nextInt(aliveGoodPlayers.size()));
            for (PlayerState wolf : werewolves) {
                wolf.setNightTarget(target.getRoleId());
            }
            log.info("狼人随机选择了杀死玩家 {}", target.getRoleId());
        }
    }

    private void randomSeerTarget(GameState gameState, PlayerState seer) {
        List<PlayerState> others = gameState.getPlayers().stream()
                .filter(p -> p.isAlive() && !p.getRoleId().equals(seer.getRoleId()))
                .toList();
        if (!others.isEmpty()) {
            PlayerState target = others.get(new Random().nextInt(others.size()));
            seer.setNightTarget(target.getRoleId());
            log.info("预言家随机查验了玩家 {}", target.getRoleId());
        }
    }

    private void randomWitchAction(GameState gameState, PlayerState witch) {
        PlayerState killed = getPlayerById(gameState, getKilledPlayerId(gameState));
        if (killed != null && !witch.isWitchSaveUsed() && new Random().nextBoolean()) {
            killed.setSaved(true);
            witch.setWitchSaveUsed(true);
            log.info("女巫随机救了玩家 {}", killed.getRoleId());
        }
        if (!witch.isWitchPoisonUsed() && new Random().nextBoolean()) {
            List<PlayerState> others = gameState.getPlayers().stream()
                    .filter(p -> p.isAlive() && !p.getRoleId().equals(witch.getRoleId()))
                    .toList();
            if (!others.isEmpty()) {
                PlayerState target = others.get(new Random().nextInt(others.size()));
                target.setPoisoned(true);
                witch.setWitchPoisonUsed(true);
                log.info("女巫随机毒死了玩家 {}", target.getRoleId());
            }
        }
    }

    private void randomVote(GameState gameState, PlayerState voter, Map<String, Integer> voteCount) {
        List<PlayerState> alivePlayers = gameState.getPlayers().stream()
                .filter(p -> p.isAlive() && !p.getRoleId().equals(voter.getRoleId()))
                .toList();
        if (!alivePlayers.isEmpty()) {
            PlayerState target = alivePlayers.get(new Random().nextInt(alivePlayers.size()));
            voteCount.merge(target.getRoleId(), 1, Integer::sum);
            log.info("玩家 {} 随机投票给 {}", voter.getRoleId(), target.getRoleId());
            voter.setNightTarget(target.getRoleId()); // 临时存储投票目标
        }
    }

    private void randomHunterCounterattack(GameState gameState, PlayerState hunter) {
        List<PlayerState> alivePlayers = gameState.getPlayers().stream()
                .filter(p -> p.isAlive() && !p.getRoleId().equals(hunter.getRoleId()))
                .toList();
        if (!alivePlayers.isEmpty() && new Random().nextBoolean()) {
            PlayerState target = alivePlayers.get(new Random().nextInt(alivePlayers.size()));
            target.setAlive(false);
            log.info("猎人随机反击，带走了玩家 {}", target.getRoleId());
        }
    }
}
