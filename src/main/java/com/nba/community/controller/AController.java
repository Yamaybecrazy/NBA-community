package com.nba.community.controller;

import com.nba.community.service.AlphaService;
//import com.sun.deploy.net.HttpResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

@Controller
@RequestMapping("/alpha")
public class AController {

    @Autowired
    private AlphaService alphaService;
    @RequestMapping("/hello")
    @ResponseBody
    public String sayHello(){
        return "HELLO";
    }

    @RequestMapping("/data")
    @ResponseBody
    public String getData(){
        return alphaService.find();
    }

    @RequestMapping("/http")
    public void http(HttpServletRequest request, HttpServletResponse response){
//        获取请求数据
        System.out.println(request.getMethod());
        System.out.println(request.getServletPath());
        Enumeration<String> enumeration = request.getHeaderNames();
        while (enumeration.hasMoreElements()){
            String name = enumeration.nextElement();
            String value = request.getHeader(name);
            System.out.println(name + ":" + value);
        }
        System.out.println(request.getParameter("code"));

//         返回响应数据
        response.setContentType("text/html;charset=utf-8");
        try ( PrintWriter writer = response.getWriter();){

            writer.write("<h1>hello</h1>");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

//    get请求
//    /students？current = 1&limit = 20
    @RequestMapping(path = "/students",method = RequestMethod.GET)
    @ResponseBody
    public String getStudents(
            @RequestParam(value = "current",required = false,defaultValue = "1")int current,
            @RequestParam(value = "limit",required = false,defaultValue = "1")int limit
    ){
        System.out.println(current);
        System.out.println(limit);
        return "some students";
    }

//    /student/123
    @RequestMapping(path = "/student/{id}",method = RequestMethod.GET)
    @ResponseBody
    public String getStudent(@PathVariable("id") int id){
        System.out.println(id);
        return  "a student";
    }

//    post请求
    @RequestMapping(path = "/student",method = RequestMethod.POST)
    @ResponseBody
    public String saveStudent(String name,int age){
        System.out.println(name);
        System.out.println(age);
        return "success";
    }
//相应html数据

    @RequestMapping(path = "/teacher",method = RequestMethod.GET)
    public ModelAndView getTeacher(){
        ModelAndView mav = new ModelAndView();
        mav.addObject("name","zhangsan");
        mav.addObject("age","21");
        mav.setViewName("/demo/view");
        return mav;
    }
    @RequestMapping(path = "/school",method = RequestMethod.GET)
    public String getSchool(Model model){
        model.addAttribute("name","beijing");
        model.addAttribute("age","44");
        return "/demo/view";
    }

//    相应json数据{异步请求}
    @RequestMapping(path = "/emp",method = RequestMethod.GET)
    @ResponseBody
    public Map<String,Object>getEmp(){
        Map<String,Object>emp = new HashMap<>();
        emp.put("name","zhangsan");
        emp.put("age",12);
        emp.put("salary",9090);
        return emp;
    }
    @RequestMapping(path = "/emps",method = RequestMethod.GET)
    @ResponseBody
    public List<Map<String,Object>>getEmps(){
        List<Map<String,Object>> list = new ArrayList<>();
        Map<String,Object>emp = new HashMap<>();
        emp.put("name","zhangsan");
        emp.put("age",12);
        emp.put("salary",9090);
        list.add(emp);

        emp = new HashMap<>();
        emp.put("name","erer");
        emp.put("age",12);
        emp.put("salary",23233);
        list.add(emp);
        return list;
    }
}
