package com.hmdp.utils;

import com.hmdp.entity.User;
import com.hmdp.service.IUserService;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component
public class AdminWriteInterceptor implements HandlerInterceptor {
    @Resource private IUserService userService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String method = request.getMethod();
        boolean adminPath = request.getRequestURI().startsWith(request.getContextPath() + "/admin/");
        if (!adminPath && ("GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method))) return true;
        if (UserHolder.getUser() != null) {
            User user = userService.getById(UserHolder.getUser().getId());
            if (user != null && "ADMIN".equals(user.getRole())) return true;
        }
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        return false;
    }
}
