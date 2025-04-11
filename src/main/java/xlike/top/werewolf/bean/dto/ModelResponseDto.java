package xlike.top.werewolf.bean.dto;

import lombok.Data;

import java.util.List;

/**
 * @author xlike
 */
@Data
public class OpenAiModelResponseDto {
    private String object;
    private List<ModelDto> data;
}

@Data
class ModelDto {
    private String id;
    private String object;
    private Long created;
    private String ownedBy;
}
