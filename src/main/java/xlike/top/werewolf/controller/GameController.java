package xlike.top.werewolf.controller;

import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import xlike.top.werewolf.bean.vo.ModelVo;
import xlike.top.werewolf.config.R;
import xlike.top.werewolf.service.ModelService;

import java.util.List;

/**
 * @author xlike
 */
@RestController
@AllArgsConstructor
public class ModelController {

    private final ModelService modelService;


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