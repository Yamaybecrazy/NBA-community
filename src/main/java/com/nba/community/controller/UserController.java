package com.nba.community.controller;

import com.nba.community.annotation.LoginRequired;
import com.nba.community.entity.Comment;
import com.nba.community.entity.DiscussPost;
import com.nba.community.entity.Page;
import com.nba.community.entity.User;
import com.nba.community.service.*;
import com.nba.community.util.CommunityConstant;
import com.nba.community.util.CommunityUtil;
import com.nba.community.util.HostHolder;

import com.nba.community.util.RedisKeyUtil;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.thymeleaf.TemplateEngine;

import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/user")
public class UserController implements CommunityConstant {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    @Value("${community.path.upload}")
    private String uploadPath;

    @Value("${community.path.domain}")
    private String domain;

    @Value("${server.servlet.context-path}")
    private String contextPath;

    @Autowired
    private UserService userService;

    @Autowired
    private HostHolder hostHolder;

    @Autowired
    private LikeService likeService;

    @Autowired
    private FollowService followService;

    @Autowired
    private TemplateEngine templateEngine;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private DiscussPostService discussPostService;

    @Autowired
    private CommentService commentService;

    @LoginRequired
    @RequestMapping(path = "/setting", method = RequestMethod.GET)
    public String getSettingPage() {
        return "/site/setting";
    }

    @LoginRequired
    @RequestMapping(path = "/upload", method = RequestMethod.POST)
    public String uploadHeader(MultipartFile headerImage, Model model) {
        if (headerImage == null) {
            model.addAttribute("error", "????????????????????????!");
            return "/site/setting";
        }

        String fileName = headerImage.getOriginalFilename();
        String suffix = fileName.substring(fileName.lastIndexOf("."));
        if (StringUtils.isBlank(suffix)) {
            model.addAttribute("error", "????????????????????????!");
            return "/site/setting";
        }

        // ?????????????????????
        fileName = CommunityUtil.generateUUID() + suffix;
        // ???????????????????????????
        File dest = new File(uploadPath + "/" + fileName);
        try {
            // ????????????
            headerImage.transferTo(dest);
        } catch (IOException e) {
            logger.error("??????????????????: " + e.getMessage());
            throw new RuntimeException("??????????????????,?????????????????????!", e);
        }

        // ????????????????????????????????????(web????????????)
        // http://localhost:8080/community/user/header/xxx.png
        User user = hostHolder.getUser();
        String headerUrl = domain + contextPath + "/user/header/" + fileName;
        userService.updateHeader(user.getId(), headerUrl);

        return "redirect:/index";
    }

    @RequestMapping(path = "/header/{fileName}", method = RequestMethod.GET)
    public void getHeader(@PathVariable("fileName") String fileName, HttpServletResponse response) {
        // ?????????????????????
        fileName = uploadPath + "/" + fileName;
        // ????????????
        String suffix = fileName.substring(fileName.lastIndexOf("."));
        // ????????????
        response.setContentType("image/" + suffix);
        try (
                FileInputStream fis = new FileInputStream(fileName);
                OutputStream os = response.getOutputStream();
        ) {
            byte[] buffer = new byte[1024];
            int b = 0;
            while ((b = fis.read(buffer)) != -1) {
                os.write(buffer, 0, b);
            }
        } catch (IOException e) {
            logger.error("??????????????????: " + e.getMessage());
        }
    }

    @RequestMapping(path = "/forget",method = RequestMethod.GET)
    public String forget(){
        return "/site/forget";
    }

    @RequestMapping(path = "/sendCode",method = RequestMethod.POST)
    @ResponseBody
    public String sendCode(String email,HttpServletResponse response){
        User user = userService.findUserByEmail(email);
        if(user == null){
            return CommunityUtil.getJSONString(1,"??????????????????????????????????????????");
        }
        userService.sendCode(email,response);
        return CommunityUtil.getJSONString(0);
    }

    @RequestMapping(path = "/forgetPassword",method = {RequestMethod.GET,RequestMethod.POST})
    public String forgetPassword(String email,String code,String password,Model model,
                                 @CookieValue("codeOwner") String codeOwner){
            String kaptcha = null;
            try {
                if (StringUtils.isNotBlank(codeOwner)) {
                    String redisKey = RedisKeyUtil.getCodeKey(codeOwner);
                    kaptcha = (String) redisTemplate.opsForValue().get(redisKey);
                }
            } catch (Exception e) {
                model.addAttribute("codeMsg", "???????????????!");
                return "/site/forget";
            }
            if (StringUtils.isBlank(kaptcha) || StringUtils.isBlank(code) || !kaptcha.equals(code)) {
                model.addAttribute("codeMsg", "??????????????????!");
                return "/site/forget";
            }

            Map<String, Object> map = userService.forget(email, password);
            if (map.containsKey("success")) {
                return "redirect:/login";
            } else {
                model.addAttribute("passwordMsg", map.get("passwordMsg"));
                return "/site/forget";
            }


    }

    @LoginRequired
    @RequestMapping(path = "/modifyPassword", method = RequestMethod.POST)
    public String modifyPassword(String password, String newPassword, String confirmPassword,Model model) {
        User user = hostHolder.getUser();
        password = CommunityUtil.md5(password + user.getSalt());
        newPassword = CommunityUtil.md5(newPassword + user.getSalt());
        confirmPassword = CommunityUtil.md5(confirmPassword + user.getSalt());
        if (StringUtils.isBlank(password)) {
            model.addAttribute("oldMsg", "?????????????????????!");
            return "/site/setting";
        }

        if (StringUtils.isBlank(newPassword)) {
            model.addAttribute("newMsg", "?????????????????????!");
            return "/site/setting";
        }

        if (StringUtils.isBlank(confirmPassword)) {
            model.addAttribute("confirmMsg", "?????????????????????!");
            return "/site/setting";
        }

        if (!user.getPassword().equals(password)) {
            model.addAttribute("oldMsg", "?????????????????????!");
            return "/site/setting";
        }
        if (!newPassword.equals(confirmPassword)) {
            model.addAttribute("confirmMsg", "???????????????????????????!");
            return "/site/setting";
        }

        userService.updatePassword(user.getId(),newPassword);

        return "redirect:/logout";
    }

//    ????????????
    @RequestMapping(path = "/profile/{userId}", method = RequestMethod.GET)
    public String getProfilePage(@PathVariable("userId") int userId, Model model,
                                 @RequestParam(name = "infoMode", defaultValue = "0") int infoMode) {
        User user = userService.findUserById(userId);
        if (user == null) {
            throw new RuntimeException("??????????????????!");
        }

        // ??????
        model.addAttribute("user", user);
        // ????????????
        int likeCount = likeService.findUserLikeCount(userId);
        model.addAttribute("likeCount", likeCount);

        // ????????????
        long followeeCount = followService.findFolloweeCount(userId, ENTITY_TYPE_USER);
        model.addAttribute("followeeCount", followeeCount);
        // ????????????
        long followerCount = followService.findFollowerCount(ENTITY_TYPE_USER, userId);
        model.addAttribute("followerCount", followerCount);
        // ???????????????
        boolean hasFollowed = false;
        if (hostHolder.getUser() != null) {
            hasFollowed = followService.hasFollowed(hostHolder.getUser().getId(), ENTITY_TYPE_USER, userId);
        }
        model.addAttribute("hasFollowed", hasFollowed);
        model.addAttribute("infoMode",infoMode);
        return "/site/profile";
    }

    //    ????????????
    @RequestMapping(path = "/myPost/{userId}", method = RequestMethod.GET)
    public String getMyPost(@PathVariable("userId") int userId, Model model, Page page,
                            @RequestParam(name = "infoMode", defaultValue = "1") int infoMode) {
        User user = userService.findUserById(userId);
        if (user == null) {
            throw new RuntimeException("??????????????????!");
        }
        // ??????
        model.addAttribute("user", user);
        // ??????????????????
        page.setLimit(5);
        page.setRows(discussPostService.findDiscussPostRows(user.getId()));
        page.setPath("/user/myPost/"+userId);
//        ??????????????????????????????
        List<DiscussPost> discussPosts = discussPostService.findDiscussPosts(userId, page.getOffset(), page.getLimit(),0);
        List<Map<String, Object>> list = new ArrayList<>();
        if (discussPosts != null) {
            for (DiscussPost post : discussPosts) {
                Map<String, Object> map = new HashMap<>();
                map.put("post", post);
                long likeCount = likeService.findEntityLikeCount(ENTITY_TYPE_POST, post.getId());
                map.put("likeCount", likeCount);
                list.add(map);
            }
            model.addAttribute("discussPosts", list);
        }
        // ????????????
        int postCount = discussPostService.findDiscussPostRows(user.getId());
        model.addAttribute("postCount", postCount);
        model.addAttribute("infoMode", infoMode);
        return "/site/my-post";
    }

    //    ????????????
    @RequestMapping(path = "/myComment/{userId}", method = RequestMethod.GET)
    public String getMyComment(@PathVariable("userId") int userId, Model model, Page page,
                               @RequestParam(name = "infoMode", defaultValue = "2") int infoMode) {
        User user = userService.findUserById(userId);
        if (user == null) {
            throw new RuntimeException("??????????????????!");
        }
        // ??????
        model.addAttribute("user", user);
        // ??????????????????
        page.setLimit(5);
        page.setRows(commentService.findCommentCountById(user.getId()));
        page.setPath("/user/myComment/"+userId);

// ???????????????????????? (???????????????,????????? sql ?????????????????? entity_type = 1)
        List<Comment> comments = commentService.findCommentsByUserId(user.getId(),page.getOffset(), page.getLimit());
        List<Map<String, Object>> list = new ArrayList<>();
        if (comments != null) {
            for (Comment comment : comments) {
                Map<String, Object> map = new HashMap<>();
                map.put("comment", comment);
                // ???????????? id ???????????????????????????
                String discussPostTitle = discussPostService.findDiscussPostById(comment.getEntityId()).getTitle();
                map.put("discussPostTitle", discussPostTitle);
                list.add(map);
            }
            model.addAttribute("comments", list);
        }
        // ???????????????
        int commentCount = commentService.findCommentCountById(user.getId());
        model.addAttribute("commentCount", commentCount);
        model.addAttribute("infoMode", infoMode);

        return "/site/my-reply";
    }


}
