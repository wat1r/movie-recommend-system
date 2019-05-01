package com.frankcooper.server.rest;

import com.frankcooper.server.model.core.User;
import com.frankcooper.server.model.request.LoginUserRequest;
import com.frankcooper.server.model.request.RegisterUserRequest;
import com.frankcooper.server.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Arrays;

/**
 * Created by FrankCooper
 * Date 2019/5/1 15:11
 * Description
 */
@RequestMapping("/rest/users")
@Controller
public class UserRestApi {
    @Autowired
    private UserService userService;


    /**
     * 注册{success:true}
     *
     * @param username
     * @param password
     * @param model
     * @return
     */
    @RequestMapping(value = "/register", produces = "application/json", method = RequestMethod.GET)
    @ResponseBody
    public Model addUser(@RequestParam("username") String username, @RequestParam("password") String password, Model model) {
        if (userService.checkUserExist(username)) {
            model.addAttribute("success", false);
            model.addAttribute("message", " 用户名已经被注册！");
            return model;
        }
        model.addAttribute("success", userService.registerUser(new RegisterUserRequest(username, password)));
        return model;
    }


    /**
     * @return
     */
    //用户的注册
    @RequestMapping
    public Model login() {
        //1.
        //2.
        //3.

        return null;
    }


    @RequestMapping(value = "/login", produces = "application/json", method = RequestMethod.GET)
    @ResponseBody
    public Model login(@RequestParam("username") String username, @RequestParam("password") String password, Model model) {
        User user = userService.loginUser(new LoginUserRequest(username, password));
        model.addAttribute("success", user != null);
        model.addAttribute("user", user);
        return model;
    }

    //冷启动问题
    @RequestMapping(value = "/pref", produces = "application/json", method = RequestMethod.GET)
    @ResponseBody
    public Model addPrefGenres(@RequestParam("username") String username, @RequestParam("genres") String genres, Model model) {
        User user = userService.findByUsername(username);
        user.getPrefGenres().addAll(Arrays.asList(genres.split(",")));
        user.setFirst(false);
        model.addAttribute("success", userService.updateUser(user));
        return model;
    }


}
