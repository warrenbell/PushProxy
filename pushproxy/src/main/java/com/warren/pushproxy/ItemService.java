package com.warren.pushproxy;

import javax.ws.rs.core.MediaType;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.UniformInterfaceException;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.api.json.JSONConfiguration;

public class ItemService
{
	private static WebResource itemWebResource;
	
	static
	{
		ClientConfig clientConfig = new DefaultClientConfig();
		clientConfig.getProperties().put(ClientConfig.PROPERTY_FOLLOW_REDIRECTS, true);
		clientConfig.getFeatures().put(JSONConfiguration.FEATURE_POJO_MAPPING, Boolean.TRUE);
		Client client = Client.create(clientConfig);
		itemWebResource = client.resource("http://127.0.0.1:8080/pushlocal/services/items");
	}
	
	public static String getItem(String itemId)
	{
		try
		{
			return itemWebResource.path(itemId)
					.accept(MediaType.APPLICATION_JSON)
			        .get(String.class);
		}
		catch(UniformInterfaceException uiex)
		{
			return null;
		}

	}
}
