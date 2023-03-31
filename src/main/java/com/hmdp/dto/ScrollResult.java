package com.hmdp.dto;

import lombok.Data;

import java.util.List;

//查询关注的人发送的博客的滚动分页的结果，不仅限于博客
@Data
public class ScrollResult {
    private List<?> list;
    private Long minTime;
    private Integer offset;
}
