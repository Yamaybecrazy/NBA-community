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
            model.addAttribute("error", "您还没有选择图片!");
            return "/site/setting";
        }

        String fileName = headerImage.getOriginalFilename();
        String suffix = fileName.substring(fileName.lastIndexOf("."));
        if (StringUtils.isBlank(suffix)) {
            model.addAttribute("error", "文件的格式不正确!");
            return "/site/setting";
        }

        // 生成随机文件名
        fileName = CommunityUtil.generateUUID() + suffix;
        // 确定文件存放的路径
        File dest = new File(uploadPath + "/" + fileName);
        try {
            // 存储文件
            headerImage.transferTo(dest);
        } catch (IOException e) {
            logger.error("上传文件失败: " + e.getMessage());
            throw new RuntimeException("上传文件失败,服务器发生异常!", e);
        }

        // 更新当前用户的头像的路径(web访问路径)
        // http://localhost:8080/community/user/header/xxx.png
        User user = hostHolder.getUser();
        String headerUrl = domain + contextPath + "/user/header/" + fileName;
        userService.updateHeader(user.getId(), headerUrl);

        return "redirect:/index";
    }

    @RequestMapping(path = "/header/{fileName}", method = RequestMethod.GET)
    public void getHeader(@PathVariable("fileName") String fileName, HttpServletResponse response) {
        // 服务器存放路径
        fileName = uploadPath + "/" + fileName;
        // 文件后缀
        String suffix = fileName.substring(fileName.lastIndexOf("."));
        // 响应图片
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
            logger.error("读取头像失败: " + e.getMessage());
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
            return CommunityUtil.getJSONString(1,"您输入的邮箱格式有误或未注册");
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
                model.addAttribute("codeMsg", "验证码失效!");
                return "/site/forget";
            }
            if (StringUtils.isBlank(kaptcha) || StringUtils.isBlank(code) || !kaptcha.equals(code)) {
                model.addAttribute("codeMsg", "验证码不正确!");
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
            model.addAttribute("oldMsg", "原密码不能为空!");
            return "/site/setting";
        }

        if (StringUtils.isBlank(newPassword)) {
            model.addAttribute("newMsg", "新密码不能为空!");
            return "/site/setting";
        }

        if (StringUtils.isBlank(confirmPassword)) {
            model.addAttribute("confirmMsg", "请再次输入密码!");
            return "/site/setting";
        }

        if (!user.getPassword().equals(password)) {
            model.addAttribute("oldMsg", "原始密码不正确!");
            return "/site/setting";
        }
        if (!newPassword.equals(confirmPassword)) {
            model.addAttribute("confirmMsg", "两次输入密码不正确!");
            return "/site/setting";
        }

        userService.updatePassword(user.getId(),newPassword);

        return "redirect:/logout";
    }

//    个人主页
    @RequestMapping(path = "/profile/{userId}", method = RequestMethod.GET)
    public String getProfilePage(@PathVariable("userId") int userId, Model model,
                                 @RequestParam(name = "infoMode", defaultValue = "0") int infoMode) {
        User user = userService.findUserById(userId);
        if (user == null) {
            throw new RuntimeException("该用户不存在!");
        }

        // 用户
        model.addAttribute("user", user);
        // 点赞数量
        int likeCount = likeService.findUserLikeCount(userId);
        model.addAttribute("likeCount", likeCount);

        // 关注数量
        long followeeCount = followService.findFolloweeCount(userId, ENTITY_TYPE_USER);
        model.addAttribute("followeeCount", followeeCount);
        // 粉丝数量
        long followerCount = followService.findFollowerCount(ENTITY_TYPE_USER, userId);
        model.addAttribute("followerCount", followerCount);
        // 是否已关注
        boolean hasFollowed = false;
        if (hostHolder.getUser() != null) {
            hasFollowed = followService.hasFollowed(hostHolder.getUser().getId(), ENTITY_TYPE_USER, userId);
        }
        model.addAttribute("hasFollowed", hasFollowed);
        model.addAttribute("infoMode",infoMode);
        return "/site/profile";
    }

    //    我的帖子
    @RequestMapping(path = "/myPost/{userId}", method = RequestMethod.GET)
    public String getMyPost(@PathVariable("userId") int userId, Model model, Page page,
                            @RequestParam(name = "infoMode", defaultValue = "1") int infoMode) {
        User user = userService.findUserById(userId);
        if (user == null) {
            throw new RuntimeException("该用户不存在!");
        }
        // 用户
        model.addAttribute("user", user);
        // 设置分页信息
        page.setLimit(5);
        page.setRows(discussPostService.findDiscussPostRows(user.getId()));
        page.setPath("/user/myPost/"+userId);
//        查询某用户发布的帖子
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
        // 帖子数量
        int postCount = discussPostService.findDiscussPostRows(user.getId());
        model.addAttribute("postCount", postCount);
        model.addAttribute("infoMode", infoMode);
        return "/site/my-post";
    }

    //    我的回复
    @RequestMapping(path = "/myComment/{userId}", method = RequestMethod.GET)
    public String getMyComment(@PathVariable("userId") int userId, Model model, Page page,
                               @RequestParam(name = "infoMode", defaultValue = "2") int infoMode) {
        User user = userService.findUserById(userId);
        if (user == null) {
            throw new RuntimeException("该用户不存在!");
        }
        // 用户
        model.addAttribute("user", user);
        // 设置分页信息
        page.setLimit(5);
        page.setRows(commentService.findCommentCountById(user.getId()));
        page.setPath("/user/myComment/"+userId);

// 获取用户所有评论 (而不是回复,所以在 sql 里加一个条件 entity_type = 1)
        List<Comment> comments = commentService.findCommentsByUserId(user.getId(),page.getOffset(), page.getLimit());
        List<Map<String, Object>> list = new ArrayList<>();
        if (comments != null) {
            for (Comment comment : comments) {
                Map<String, Object> map = new HashMap<>();
                map.put("comment", comment);
                // 根据实体 id 查询对应的帖子标题
                String discussPostTitle = discussPostService.findDiscussPostById(comment.getEntityId()).getTitle();
                map.put("discussPostTitle", discussPostTitle);
                list.add(map);
            }
            model.addAttribute("comments", list);
        }
        // 回复的数量
        int commentCount = commentService.findCommentCountById(user.getId());
        model.addAttribute("commentCount", commentCount);
        model.addAttribute("infoMode", infoMode);

        return "/site/my-reply";
    }


}
