package com.warren.pushproxy;

import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

public class ItemRequestInterceptor implements RequestInterceptor
{

	@Override
	public String[] getParameterValues(String name, String[] parameterValuesResult, HttpServletRequest request)
	{
		if(name.equalsIgnoreCase("item"))
		{
			String[] itemId = request.getParameterValues("itemId");
			if(itemId != null && itemId.length > 0)
			{
				return new String[]{ItemService.getItem(itemId[0])};
			}
		}
		return parameterValuesResult;
	}

	@Override
	public Map<String, String[]> getParameterMap(Map<String, String[]> parameterMapResult, HttpServletRequest request)
	{
		if(parameterMapResult == null)
		{
			parameterMapResult = new HashMap<String, String[]>();
		}
		for(String parameterName : request.getParameterMap().keySet())
		{
			if(parameterName.equalsIgnoreCase("item"))
			{
				String[] itemId = request.getParameterValues("itemId");
				if(itemId != null && itemId.length == 1)
				{
					parameterMapResult.put(parameterName, new String[]{ItemService.getItem(itemId[0])});
				}
			}
			if(!parameterMapResult.containsKey(parameterName))
			{
				parameterMapResult.put(parameterName, request.getParameterValues(parameterName));
			}
		}
		return parameterMapResult;
	}

}
