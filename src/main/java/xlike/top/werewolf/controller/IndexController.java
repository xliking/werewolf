package xlike.top.werewolf.controller;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * @author xlike
 */
@Controller
@AllArgsConstructor
public class IndexController {

    @GetMapping("/")
    public String toLogin() {
        return "index";
    }

    @GetMapping("/index")
    public String index() {
        return "index";
    }

    @GetMapping("/error")
    public String error() {
        return "error";
    }


}
