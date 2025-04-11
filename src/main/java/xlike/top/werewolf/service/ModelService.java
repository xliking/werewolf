package xlike.top.werewolf.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import xlike.top.werewolf.bean.dto.ModelResponseDto;
import xlike.top.werewolf.bean.vo.ModelVo;
import xlike.top.werewolf.utils.ChatUtils;
import xlike.top.werewolf.utils.OkHttpUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author xlike
 */
@Service
public class ModelService {

    private static final Logger logger = LoggerFactory.getLogger(ModelService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();


    public List<ModelVo> getModelList(String key, String url) {
        try {
            // 构建请求头
            Map<String, String> headers = new HashMap<>();
            headers.put("Authorization", "Bearer " + key);
            headers.put("Content-Type", "application/json");
            // 发送请求
            Response response = OkHttpUtils.get(url, headers);
            String responseBody = OkHttpUtils.getResponseBodyAsString(response);
            if (responseBody == null) {
                throw new RuntimeException("Response body is null");
            }
            // 解析响应
            System.out.println("responseBody : " + responseBody);
            ModelResponseDto responseDto = objectMapper.readValue(responseBody, ModelResponseDto.class);
            // 转换为 VO
            return responseDto.getData().stream()
                    .map(modelDto -> {
                        ModelVo vo = new ModelVo();
                        vo.setId(modelDto.getId());
                        vo.setOwnedBy(modelDto.getOwned_by());
                        return vo;
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            logger.error("Failed to get OpenAI models from URL {}: {}", url, e.getMessage(), e);
            throw new RuntimeException("Failed to get models: " + e.getMessage());
        }
    }

    public boolean modelTest(String key, String url, String modelName) {
        return ChatUtils.isModelValid(url, key, modelName);
    }
}
