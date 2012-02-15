package com.warren.pushproxy;

import java.util.Map;

import javax.servlet.http.HttpServletRequest;

public interface RequestInterceptor
{
	public String[] getParameterValues(String name, String[] parameterValuesResult, HttpServletRequest request);
	public Map<String, String[]> getParameterMap(Map<String, String[]> parameterMapResult, HttpServletRequest request);
}
