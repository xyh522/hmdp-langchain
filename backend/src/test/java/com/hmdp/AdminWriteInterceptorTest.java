package com.hmdp;

import com.hmdp.entity.User;
import com.hmdp.service.IUserService;
import com.hmdp.utils.AdminWriteInterceptor;
import com.hmdp.utils.UserHolder;
import com.hmdp.dto.UserDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminWriteInterceptorTest {
    private final IUserService users = mock(IUserService.class);
    private final AdminWriteInterceptor interceptor = new AdminWriteInterceptor();

    AdminWriteInterceptorTest() {
        ReflectionTestUtils.setField(interceptor, "userService", users);
    }

    @AfterEach
    void clearUser() { UserHolder.removeUser(); }

    @Test
    void rejectsAnonymousAdminRead() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/shops");
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertEquals(403, response.getStatus());
    }

    @Test
    void rejectsUserVoucherWrite() {
        UserDTO user = new UserDTO();
        user.setId(10L);
        UserHolder.saveUser(user);
        when(users.getById(10L)).thenReturn(new User().setRole("USER"));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/voucher/seckill");
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertEquals(403, response.getStatus());
    }

    @Test
    void allowsDatabaseAdmin() {
        UserDTO user = new UserDTO();
        user.setId(11L);
        UserHolder.saveUser(user);
        when(users.getById(11L)).thenReturn(new User().setRole("ADMIN"));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/shops");
        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
    }
}
