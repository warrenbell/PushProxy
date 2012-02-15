package com.warren.pushproxy;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name="item")
public class Item
{
	public Item()
	{
		super();
	}
	
	public Item(String itemId, String description)
	{
		super();
		this.itemId = itemId;
		this.description = description;
	}

	private String itemId;
	private String description;
	
	public String getItemId()
	{
		return itemId;
	}
	public void setItemId(String itemId)
	{
		this.itemId = itemId;
	}
	public String getDescription()
	{
		return description;
	}
	public void setDescription(String description)
	{
		this.description = description;
	}
	
}
