package com.warren.pushproxy;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;

public class ProxiedRequestWrapper extends HttpServletRequestWrapper
{
	private List<RequestInterceptor> requestInterceptors;
	
	public ProxiedRequestWrapper(HttpServletRequest request)
	{
		this(request, new RequestInterceptor[]{});
	}
	
	public ProxiedRequestWrapper(HttpServletRequest request, RequestInterceptor[] requestInterceptors)
	{
		super(request);
		this.requestInterceptors = Arrays.asList(requestInterceptors);
	}

	public ProxiedRequestWrapper(HttpServletRequest request, List<RequestInterceptor> requestInterceptors)
	{
		super(request);
		this.requestInterceptors = requestInterceptors;
	}
	
	@Override
	public Map<String, String[]> getParameterMap()
	{
		Map<String, String[]> parameterMapResult = null;
		for(RequestInterceptor requestInterceptor : requestInterceptors)
		{
			parameterMapResult = requestInterceptor.getParameterMap(parameterMapResult, (HttpServletRequest)getRequest());
		}
		if(parameterMapResult != null)
		{
			return parameterMapResult;
		}
		return super.getParameterMap();
	}

	@Override
	public String[] getParameterValues(String name)
	{
		String[] parameterValuesResult = null;
		for(RequestInterceptor requestInterceptor : requestInterceptors)
		{
			parameterValuesResult = requestInterceptor.getParameterValues(name, parameterValuesResult, (HttpServletRequest)getRequest());
		}
		if(parameterValuesResult != null)
		{
			return parameterValuesResult;
		}
		return super.getParameterValues(name);
	}

}
