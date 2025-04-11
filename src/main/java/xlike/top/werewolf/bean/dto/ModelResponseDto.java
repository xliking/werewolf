package xlike.top.werewolf.bean.dto;

import lombok.Data;

import java.util.List;

/**
 * @author xlike
 */
@Data
public class ModelResponseDto {
    private String object;
    private List<ModelDto> data;
}
