package com.hmdp.utils;

import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;

public class UserHolder {

    private static final ThreadLocal<UserDTO> tl = new ThreadLocal<>();

    public static void saveUser(UserDTO user){
        tl.set(user);
    }

    public static UserDTO getUser(){
        return tl.get();
    }

    public static void removeUser(){
        tl.remove();
    }


//    /**
//     * 原来是是UserDTO的，让我都改成了User
//     */
//
//    private static final ThreadLocal<User> tl = new ThreadLocal<>();
//
//    public static void saveUser(User user){
//        tl.set(user);
//    }
//
//    public static User getUser(){
//        return tl.get();
//    }
//
//    public static void removeUser(){
//        tl.remove();
//    }
}