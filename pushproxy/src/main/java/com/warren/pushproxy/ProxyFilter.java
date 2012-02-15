package com.warren.pushproxy;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class ProxyFilter implements Filter
{

	@Override
	public void init(FilterConfig filterConfig) throws ServletException
	{
	}

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException
	{
		ProxiedRequestWrapper proxiedRequest = new ProxiedRequestWrapper((HttpServletRequest)request, new RequestInterceptor[]{new ItemRequestInterceptor()});
		ProxiedResponseWrapper proxiedResponse = new ProxiedResponseWrapper((HttpServletResponse)response);
		chain.doFilter(proxiedRequest, proxiedResponse);
	}

	@Override
	public void destroy()
	{
	}

}
