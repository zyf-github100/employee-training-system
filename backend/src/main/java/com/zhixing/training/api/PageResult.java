package com.zhixing.training.api;

import java.util.List;

public record PageResult<T>(List<T> list, int pageNo, int pageSize, long total) { }
