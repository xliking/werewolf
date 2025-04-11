package xlike.top.token.controller;

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
        return "login";
    }


    @GetMapping("/login")
    public String login() {
        return "login";
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
