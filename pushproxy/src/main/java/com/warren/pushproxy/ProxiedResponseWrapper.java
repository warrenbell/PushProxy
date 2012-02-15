package com.warren.pushproxy;

import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

public class ProxiedResponseWrapper extends HttpServletResponseWrapper
{

	public ProxiedResponseWrapper(HttpServletResponse response)
	{
		super(response);
	}

}
