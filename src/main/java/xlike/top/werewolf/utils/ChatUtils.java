package xlike.top.werewolf.utils;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.net.Proxy;
import java.util.*;

/**
 * AI 请求工具类，支持模型列表查询、模型有效性检查和带历史记录的对话
 *
 * @author xlike
 */
public class ChatUtils {

    private static final Logger logger = LoggerFactory.getLogger(ChatUtils.class);
    private static final String CHAT_HISTORY_PREFIX = "ai:chat:history:";
    // 聊天历史缓存7天
    private static final long CHAT_HISTORY_EXPIRE_SECONDS = 7 * 24 * 60 * 60;
    private static final String MODELS_ENDPOINT = "/v1/models";
    private static final String CHAT_ENDPOINT = "/v1/chat/completions";

    /**
     * 智能补全API URL，确保正确的后缀
     *
     * @param baseUrl      基础URL
     * @param endpointType 端点类型（models 或 chat）
     * @return 补全后的URL
     */
    private static String completeApiUrl(String baseUrl, String endpointType) {
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("API URL cannot be empty");
        }

        // 移除末尾的斜杠
        String trimmedUrl = baseUrl.trim().replaceAll("/+$", "");

        // 检查是否已经包含正确的后缀
        if (endpointType.equals("models") && trimmedUrl.endsWith(MODELS_ENDPOINT)) {
            return trimmedUrl;
        } else if (endpointType.equals("chat") && trimmedUrl.endsWith(CHAT_ENDPOINT)) {
            return trimmedUrl;
        }

        // 移除可能存在的其他后缀
        if (trimmedUrl.contains("/v1/")) {
            int index = trimmedUrl.indexOf("/v1/") + 4;
            trimmedUrl = trimmedUrl.substring(0, index);
        }

        // 补全正确的后缀
        if (endpointType.equals("models")) {
            return trimmedUrl + MODELS_ENDPOINT;
        } else {
            return trimmedUrl + CHAT_ENDPOINT;
        }
    }

    public static List<String> getModelList(String apiUrl, String apiKey) throws IOException {
        // 智能补全URL
        return getModelList(apiUrl, apiKey, null);
    }


    /**
     * 获取AI模型列表，不使用缓存
     *
     * @param apiUrl API地址
     * @param apiKey API密钥
     * @param proxy  代理服务器，可选
     * @return 模型列表
     * @throws IOException 请求异常
     */
    public static List<String> getModelList(String apiUrl, String apiKey, Proxy proxy) throws IOException {
        // 智能补全URL
        String completeUrl = completeApiUrl(apiUrl, "models");
        logger.info("请求模型列表，URL: {}", completeUrl);

        // 构建请求头
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + apiKey);
        headers.put("Content-Type", "application/json");

        // 发送请求
        Response response = OkHttpUtils.get(completeUrl, headers, proxy);
        String responseBody = OkHttpUtils.getResponseBodyAsString(response);

        try {
            // 解析响应
            JSONObject jsonObject = JSON.parseObject(responseBody);
            JSONArray modelsArray = jsonObject.getJSONArray("data");
            List<String> modelList = new ArrayList<>();
            for (int i = 0; i < modelsArray.size(); i++) {
                JSONObject model = modelsArray.getJSONObject(i);
                String modelId = model.getString("id");
                if (modelId != null) {
                    modelList.add(modelId);
                }
            }
            logger.info("获取模型列表成功，数量: {}", modelList.size());
            return modelList;
        } finally {
            OkHttpUtils.closeResponse(response);
        }
    }

    public static boolean isModelValid(String apiUrl, String apiKey, String model){
        return isModelValid(apiUrl, apiKey, model, null);
    }


   /**
    * 检查模型是否有效（通过发送一次简单的对话请求）
    *
    * @param apiUrl API地址
    * @param apiKey API密钥
    * @param model  模型名称
    * @param proxy  代理服务器，可选
    * @return 是否有效
    */
    public static boolean isModelValid(String apiUrl, String apiKey, String model, Proxy proxy) {
        try {
            // 智能补全URL
            String completeUrl = completeApiUrl(apiUrl, "chat");

            // 构建简单的测试消息
            List<Map<String, String>> messages = new ArrayList<>();
            Map<String, String> userMessage = new HashMap<>();
            userMessage.put("role", "user");
            userMessage.put("content", "Hello");
            messages.add(userMessage);

            // 发送请求
            JSONObject requestBody = new JSONObject();
            requestBody.put("model", model);
            requestBody.put("messages", messages);
            requestBody.put("stream", false);

            // 构建请求头
            Map<String, String> headers = new HashMap<>();
            headers.put("Authorization", "Bearer " + apiKey);
            headers.put("Content-Type", "application/json");

            // 发送请求
            logger.info("测试模型有效性，模型: {}, URL: {}", model, completeUrl);
            RequestBody body = OkHttpUtils.createJsonRequestBody(requestBody.toJSONString());
            Response response = OkHttpUtils.post(completeUrl, headers, body, proxy);
            String responseBody = OkHttpUtils.getResponseBodyAsString(response);

            // 检查响应状态码和内容
            if (response.code() == 200 && responseBody != null) {
                JSONObject jsonResponse = JSON.parseObject(responseBody);
                JSONArray choices = jsonResponse.getJSONArray("choices");
                if (choices != null && !choices.isEmpty()) {
                    logger.info("模型 {} 测试通过", model);
                    return true;
                }
            }
            logger.warn("模型 {} 测试失败，状态码: {}", model, response.code());
            return false;
        } catch (Exception e) {
            logger.error("测试模型 {} 时发生错误: {}", model, e.getMessage());
            return false;
        }
    }

    public static String sendChatRequest(String apiUrl, String apiKey, String model, String userId, String message)
            throws IOException {
        return sendChatRequest(apiUrl, apiKey, model, userId, message, true, 10, null);
    }

    /**
     * 发送AI对话请求，支持历史记录
     *
     * @param apiUrl             API地址
     * @param apiKey             API密钥
     * @param model              模型名称
     * @param userId             用户ID，用于存储历史记录
     * @param message            当前用户消息
     * @param useHistory         是否使用历史记录
     * @param maxHistoryMessages 最大历史记录消息数
     * @param proxy              代理服务器，可选
     * @return AI响应消息
     * @throws IOException 请求异常
     */
    public static String sendChatRequest(String apiUrl, String apiKey, String model, String userId, String message,
                                         boolean useHistory, int maxHistoryMessages, Proxy proxy) throws IOException {
        // 智能补全URL
        String completeUrl = completeApiUrl(apiUrl, "chat");

        // 构建消息列表
        List<Map<String, String>> messages = new ArrayList<>();

        // 获取历史记录
        if (useHistory && userId != null) {
            String historyKey = CHAT_HISTORY_PREFIX + userId;
            List<Object> history = RedisUtil.lget(historyKey, 0, maxHistoryMessages - 1);
            if (history != null && !history.isEmpty()) {
                for (Object historyMsg : history) {
                    Map<String, String> msgMap = JSON.parseObject(historyMsg.toString(), Map.class);
                    messages.add(msgMap);
                }
                logger.info("用户 {} 加载历史记录，数量: {}", userId, history.size());
            }
        }

        // 添加当前用户消息
        Map<String, String> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", message);
        messages.add(userMessage);

        // 构建请求体
        JSONObject requestBody = new JSONObject();
        requestBody.put("model", model);
        requestBody.put("messages", messages);
        requestBody.put("stream", false);

        // 构建请求头
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + apiKey);
        headers.put("Content-Type", "application/json");

        // 发送请求
        logger.info("发送对话请求，模型: {}, 用户: {}, 消息: {}", model, userId, message);
        RequestBody body = OkHttpUtils.createJsonRequestBody(requestBody.toJSONString());
        Response response = OkHttpUtils.post(completeUrl, headers, body, proxy);
        String responseBody = OkHttpUtils.getResponseBodyAsString(response);

        try {
            // 解析响应
            if (response.code() == 200 && responseBody != null) {
                JSONObject jsonResponse = JSON.parseObject(responseBody);
                JSONArray choices = jsonResponse.getJSONArray("choices");
                if (choices != null && !choices.isEmpty()) {
                    JSONObject choice = choices.getJSONObject(0);
                    JSONObject messageObj = choice.getJSONObject("message");
                    String aiResponse = messageObj.getString("content");

                    // 保存到历史记录
                    if (useHistory && userId != null) {
                        String historyKey = CHAT_HISTORY_PREFIX + userId;
                        // 保存用户消息
                        RedisUtil.rpush(historyKey, JSON.toJSONString(userMessage));
                        // 保存AI响应
                        Map<String, String> aiMessage = new HashMap<>();
                        aiMessage.put("role", "assistant");
                        aiMessage.put("content", aiResponse);
                        RedisUtil.rpush(historyKey, JSON.toJSONString(aiMessage));
                        // 设置过期时间
                        RedisUtil.expire(historyKey, CHAT_HISTORY_EXPIRE_SECONDS);
                        logger.info("用户 {} 聊天记录已保存", userId);
                    }

                    logger.info("对话成功，模型: {}, 响应: {}", model, aiResponse);
                    return aiResponse;
                }
            }
            logger.error("对话请求失败，状态码: {}, 响应: {}", response.code(), responseBody);
            throw new IOException("AI chat request failed, status code: " + response.code());
        } finally {
            OkHttpUtils.closeResponse(response);
        }
    }

    /**
     * 清除用户聊天历史记录
     *
     * @param userId 用户ID
     * @return 是否成功
     */
    public static boolean clearChatHistory(String userId) {
        if (userId == null) {
            logger.warn("用户ID为空，无法清除历史记录");
            return false;
        }
        String historyKey = CHAT_HISTORY_PREFIX + userId;
        RedisUtil.del(historyKey);
        logger.info("用户 {} 聊天历史记录已清除", userId);
        return true;
    }

    /**
     * 获取用户聊天历史记录
     *
     * @param userId 用户ID
     * @param limit  最大记录数
     * @return 历史记录列表
     */
    public static List<Map<String, String>> getChatHistory(String userId, int limit) {
        if (userId == null) {
            logger.warn("用户ID为空，无法获取历史记录");
            return new ArrayList<>();
        }
        String historyKey = CHAT_HISTORY_PREFIX + userId;
        List<Object> history = RedisUtil.lget(historyKey, 0, limit - 1);
        List<Map<String, String>> result = new ArrayList<>();
        if (history != null && !history.isEmpty()) {
            for (Object historyMsg : history) {
                Map<String, String> msgMap = JSON.parseObject(historyMsg.toString(), Map.class);
                result.add(msgMap);
            }
        }
        logger.info("获取用户 {} 聊天历史记录，数量: {}", userId, result.size());
        return result;
    }
}
